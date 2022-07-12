package cn.how2j.diytomcat.catalina;

import cn.how2j.diytomcat.classloader.WebappClassLoader;
import cn.how2j.diytomcat.exception.WebConfigDuplicatedException;
import cn.how2j.diytomcat.http.ApplicationContext;
import cn.how2j.diytomcat.http.StandardServletConfig;
import cn.how2j.diytomcat.util.ContextXMLUtil;
import cn.how2j.diytomcat.watcher.ContextFileChangeWatcher;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;
import org.apache.jasper.JspC;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.util.*;

public class Context { // 代表一个应用

    private String path; // 表示访问的路径
    private String docBase; // 表示对应在文件系统中的位置
    private Host host;
    private boolean reloadable;
    private ServletContext servletContext;
    private File contextWebXmlFile; // 对应 XXX/WEB-INF/web.xml 文件

    // 配置 Serlvet
    private Map<String, String> url_servletClassName; // 地址对应 Servlet 的类名
    private Map<String, String> url_servletName; // 地址对应 Servlet 的名称
    private Map<String, String> servletName_className; // Servlet 的名称对应类名
    private Map<String, String> className_servletName; // Servlet 类名对应名称
    private Map<String, Map<String, String>> servlet_className_init_params; // Serlvet 初始化信息
    private Map<Class<?>, HttpServlet> servletPool; // Serlvet 缓存池

    // 配置 Filter，类似于Servlet
    private Map<String, List<String>> url_filterClassName;
    private Map<String, List<String>> url_FilterNames;
    private Map<String, String> filterName_className;
    private Map<String, String> className_filterName;
    private Map<String, Map<String, String>> filter_className_init_params;
    private Map<String, Filter> filterPool; // Filter 缓存池

    private List<ServletContextListener> listeners; // 配置监听器 Listener
    private List<String> loadOnStartupServletClassNames; // 需要自启动的类
    private WebappClassLoader webappClassLoader;
    private ContextFileChangeWatcher contextFileChangeWatcher;

    public Context(String path, String docBase, Host host, boolean reloadable) {
        TimeInterval timeInterval = DateUtil.timer();
        this.path = path;
        this.docBase = docBase;
        this.host = host;
        this.reloadable = reloadable;
        this.servletContext = new ApplicationContext(this);
        this.contextWebXmlFile = new File(docBase, ContextXMLUtil.getWatchedResource());

        this.url_servletClassName = new HashMap<>();
        this.url_servletName = new HashMap<>();
        this.servletName_className = new HashMap<>();
        this.className_servletName = new HashMap<>();
        this.servlet_className_init_params = new HashMap<>();
        this.servletPool = new HashMap<>();

        this.url_filterClassName = new HashMap<>();
        this.url_FilterNames = new HashMap<>();
        this.filterName_className = new HashMap<>();
        this.className_filterName = new HashMap<>();
        this.filter_className_init_params = new HashMap<>();
        this.filterPool = new HashMap<>();

        listeners=new ArrayList<ServletContextListener>();
        this.loadOnStartupServletClassNames = new ArrayList<>();
        // 每个web应用有自己独立的类加载器，不同应用间存在同名类也不影响
        ClassLoader commonClassLoader = Thread.currentThread().getContextClassLoader();
        this.webappClassLoader = new WebappClassLoader(docBase, commonClassLoader);
        LogFactory.get().info("Deploying web application directory {}", this.docBase);
        deploy();
        LogFactory.get().info("Deployment of web application directory {} has finished in {} ms", this.docBase,timeInterval.intervalMs());
    }

    private void deploy() {
        loadListeners();
//        TimeInterval timeInterval = DateUtil.timer();
//        LogFactory.get().info("Deploying web application directory {}", this.docBase);
        init();
//        LogFactory.get().info("Deployment of web application directory {} has finished in {} ms",this.getDocBase(),timeInterval.intervalMs());

        if (reloadable) {
            contextFileChangeWatcher = new ContextFileChangeWatcher(this);
            contextFileChangeWatcher.start();
        }
        // 为了能够在jsp所转换的 java 文件里的 javax.servlet.jsp.JspFactory.getDefaultFactory() 这行能够有返回值
        JspC c = new JspC();
        new JspRuntimeContext(servletContext, c);
    }

    private void init() {
        if (!contextWebXmlFile.exists())
            return;

        try {
            checkDuplicated();
        } catch (WebConfigDuplicatedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        String xml = FileUtil.readUtf8String(contextWebXmlFile);
        Document d = Jsoup.parse(xml);

        parseServletMapping(d);
        parseServletInitParams(d);
        parseFilterMapping(d);
        parseFilterInitParams(d);
        initFilter();

        parseLoadOnStartup(d);
        handleLoadOnStartup(); // 自启动

        fireEvent("init");
    }

    public void stop() {
        webappClassLoader.stop();
        contextFileChangeWatcher.stop();
        destroyServlets();
        fireEvent("destroy");
    }

    private void parseServletMapping(Document d) {
        // url_ServletName
        Elements mappingurlElements = d.select("servlet-mapping url-pattern");
        for (Element mappingurlElement : mappingurlElements) {
            String urlPattern = mappingurlElement.text();
            String servletName = mappingurlElement.parent().select("servlet-name").first().text();
            url_servletName.put(urlPattern, servletName);
        }
        // servletName_className / className_servletName
        Elements servletNameElements = d.select("servlet servlet-name");
        for (Element servletNameElement : servletNameElements) {
            String servletName = servletNameElement.text();
            String servletClass = servletNameElement.parent().select("servlet-class").first().text();
            servletName_className.put(servletName, servletClass);
            className_servletName.put(servletClass, servletName);
        }
        // url_servletClassName
        Set<String> urls = url_servletName.keySet();
        for (String url : urls) {
            String servletName = url_servletName.get(url);
            String servletClassName = servletName_className.get(servletName);
            url_servletClassName.put(url, servletClassName);
        }
    }

    private void checkDuplicated(Document d, String mapping, String desc) throws WebConfigDuplicatedException {
        Elements elements = d.select(mapping);
        // 判断逻辑是放入一个集合，然后把集合排序之后看相邻两个元素是否相同
        List<String> contents = new ArrayList<>();
        for (Element e : elements) {
            contents.add(e.text());
        }

        Collections.sort(contents);

        for (int i = 0; i < contents.size() - 1; i++) {
            String contentPre = contents.get(i);
            String contentNext = contents.get(i + 1);
            if (contentPre.equals(contentNext)) {
                throw new WebConfigDuplicatedException(StrUtil.format(desc, contentPre));
            }
        }
    }

    private void checkDuplicated() throws WebConfigDuplicatedException {
        String xml = FileUtil.readUtf8String(contextWebXmlFile);
        Document d = Jsoup.parse(xml);

        checkDuplicated(d, "servlet-mapping url-pattern", "servlet url 重复,请保持其唯一性:{} ");
        checkDuplicated(d, "servlet servlet-name", "servlet 名称重复,请保持其唯一性:{} ");
        checkDuplicated(d, "servlet servlet-class", "servlet 类名重复,请保持其唯一性:{} ");
    }

    private void parseServletInitParams(Document d) {
        Elements servletClassNameElements = d.select("servlet-class");
        for (Element servletClassNameElement : servletClassNameElements) {
            String servletClassName = servletClassNameElement.text();
            Elements initElements = servletClassNameElement.parent().select("init-param");
            if (initElements.isEmpty())
                continue;
            Map<String, String> initParams = new HashMap<>();
            for (Element element : initElements) {
                String name = element.select("param-name").get(0).text();
                String value = element.select("param-value").get(0).text();
                initParams.put(name, value);
            }
            servlet_className_init_params.put(servletClassName, initParams);
        }
//		System.out.println("class_name_init_params:" + servlet_className_init_params);
    }

    public void parseFilterMapping(Document d) {
        // filter_url_name
        Elements mappingurlElements = d.select("filter-mapping url-pattern");
        for (Element mappingurlElement : mappingurlElements) {
            String urlPattern = mappingurlElement.text();
            String filterName = mappingurlElement.parent().select("filter-name").first().text();

            List<String> filterNames = url_FilterNames.computeIfAbsent(urlPattern, k -> new ArrayList<>());
            filterNames.add(filterName);
        }
        // class_name_filter_name
        Elements filterNameElements = d.select("filter filter-name");
        for (Element filterNameElement : filterNameElements) {
            String filterName = filterNameElement.text();
            String filterClass = filterNameElement.parent().select("filter-class").first().text();
            filterName_className.put(filterName, filterClass);
            className_filterName.put(filterClass, filterName);
        }
        // url_filterClassName

        Set<String> urls = url_FilterNames.keySet();
        for (String url : urls) {
            List<String> filterNames = url_FilterNames.computeIfAbsent(url, k -> new ArrayList<>());
            for (String filterName : filterNames) {
                String filterClassName = filterName_className.get(filterName);
                List<String> filterClassNames = url_filterClassName.computeIfAbsent(url, k -> new ArrayList<>());
                filterClassNames.add(filterClassName);
            }
        }
    }

    private void parseFilterInitParams(Document d) {
        Elements filterClassNameElements = d.select("filter-class");
        for (Element filterClassNameElement : filterClassNameElements) {
            String filterClassName = filterClassNameElement.text();
            Elements initElements = filterClassNameElement.parent().select("init-param");
            if (initElements.isEmpty())
                continue;
            Map<String, String> initParams = new HashMap<>();
            for (Element element : initElements) {
                String name = element.select("param-name").get(0).text();
                String value = element.select("param-value").get(0).text();
                initParams.put(name, value);
            }
            filter_className_init_params.put(filterClassName, initParams);
        }
    }

    private void initFilter() {
        Set<String> classNames = className_filterName.keySet();
        for (String className : classNames) {
            try { // 在将filter对象放入缓存池前进行初始化
                Class<?> clazz =  this.getWebClassLoader().loadClass(className);
                Map<String,String> initParameters = filter_className_init_params.get(className);
                String filterName = className_filterName.get(className);
                FilterConfig filterConfig = new StandardFilterConfig(servletContext, filterName, initParameters);
                Filter filter = filterPool.get(clazz);
                if(null==filter) {
                    filter = (Filter) ReflectUtil.newInstance(clazz);
                    filter.init(filterConfig);
                    filterPool.put(className, filter);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean match(String pattern, String uri) {
        // 完全匹配
        if(StrUtil.equals(pattern, uri))
            return true;
        // /* 模式
        if(StrUtil.equals(pattern, "/*"))
            return true;
        // 后缀名 /*.jsp
        if(StrUtil.startWith(pattern, "/*.")) {
            String patternExtName = StrUtil.subAfter(pattern, '.', false);
            String uriExtName = StrUtil.subAfter(uri, '.', false);
            return StrUtil.equals(patternExtName, uriExtName);
        }
        // 其他模式就懒得管了
        return false;
    }

    public List<Filter> getMatchedFilters(String uri) {
        List<Filter> filters = new ArrayList<>();
        Set<String> patterns = url_filterClassName.keySet();
        Set<String> matchedPatterns = new HashSet<>();
        for (String pattern : patterns) {
            if(match(pattern,uri)) {
                matchedPatterns.add(pattern);
            }
        }
        Set<String> matchedFilterClassNames = new HashSet<>();
        for (String pattern : matchedPatterns) {
            List<String> filterClassName = url_filterClassName.get(pattern);
            matchedFilterClassNames.addAll(filterClassName);
        }
        for (String filterClassName : matchedFilterClassNames) {
            Filter filter = filterPool.get(filterClassName);
            filters.add(filter);
        }
        return filters;
    }

    private void fireEvent(String type) {
        ServletContextEvent event = new ServletContextEvent(servletContext);
        for (ServletContextListener servletContextListener : listeners) {
            if("init".equals(type))
                servletContextListener.contextInitialized(event);
            if("destroy".equals(type))
                servletContextListener.contextDestroyed(event);
        }
    }

    public void addListener(ServletContextListener listener){
        listeners.add(listener);
    }

    public void removeListener(ServletContextListener listener){
        listeners.remove(listener);
    }

    private void loadListeners()  {
        try {
            if(!contextWebXmlFile.exists())
                return;
            String xml = FileUtil.readUtf8String(contextWebXmlFile);
            Document d = Jsoup.parse(xml);
            Elements es = d.select("listener listener-class");
            for (Element e : es) {
                String listenerClassName = e.text();
                Class<?> clazz= this.getWebClassLoader().loadClass(listenerClassName);
                ServletContextListener listener = (ServletContextListener) clazz.newInstance();
                addListener(listener);
            }
        } catch (IORuntimeException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void parseLoadOnStartup(Document d) {
        Elements es = d.select("load-on-startup");
        for (Element e : es) {
            String loadOnStartupServletClassName = e.parent().select("servlet-class").text();
            loadOnStartupServletClassNames.add(loadOnStartupServletClassName);
        }
    }

    public void handleLoadOnStartup() {
        for (String loadOnStartupServletClassName : loadOnStartupServletClassNames) {
            try {
                Class<?> clazz = webappClassLoader.loadClass(loadOnStartupServletClassName);
                getServlet(clazz);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | ServletException e) {
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        host.reload(this);
    }

    public synchronized HttpServlet getServlet(Class<?> clazz)
            throws InstantiationException, IllegalAccessException, ServletException {
        HttpServlet servlet = servletPool.get(clazz);
        if (null == servlet) { // 在将servlet对象放入缓存池前进行初始化
            servlet = (HttpServlet) clazz.newInstance();
            ServletContext servletContext = this.getServletContext();
            String className = clazz.getName();
            String servletName = className_servletName.get(className);
            Map<String, String> initParameters = servlet_className_init_params.get(className);
            ServletConfig servletConfig = new StandardServletConfig(servletContext, servletName, initParameters);
            servlet.init(servletConfig);
            servletPool.put(clazz, servlet);
        }
        return servlet;
    }

    private void destroyServlets() {
        Collection<HttpServlet> servlets = servletPool.values();
        for (HttpServlet servlet : servlets) {
            servlet.destroy();
        }
    }

    public String getServletClassName(String uri) {
        return url_servletClassName.get(uri);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDocBase() {
        return docBase;
    }

    public void setDocBase(String docBase) {
        this.docBase = docBase;
    }

    public WebappClassLoader getWebappClassLoader() {
        return webappClassLoader;
    }

    public boolean isReloadable() {
        return reloadable;
    }

    public void setReloadable(boolean reloadable) {
        this.reloadable = reloadable;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public WebappClassLoader getWebClassLoader() {
        return webappClassLoader;
    }
}
