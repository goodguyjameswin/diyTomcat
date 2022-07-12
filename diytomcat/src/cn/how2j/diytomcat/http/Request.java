package cn.how2j.diytomcat.http;

import cn.how2j.diytomcat.catalina.*;
import cn.how2j.diytomcat.util.MiniBrowser;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

public class Request extends BaseRequest {

    private Connector connector;
    private String requestString; // 请求信息
    private String uri;
    private final Socket socket;
    private Context context;
    private String method;
    private String queryString;
    private Map<String, String[]> parameterMap; // 请求参数信息
    private Map<String, String> headerMap; // 头信息
    private Cookie[] cookies;
    private HttpSession session;
    private boolean forwarded; // 是否已经进行服务端跳转
    private Map<String, Object> attributesMap; // 服务端跳转传参

    public Request(Socket socket, Connector connector) throws IOException {
        this.socket = socket;
        this.connector = connector;
        this.parameterMap = new HashMap<>();
        this.headerMap = new HashMap<>();
        this.attributesMap = new HashMap<>();

        parseHttpRequest();
        if (StrUtil.isEmpty(requestString)) return;
        parseUri();
        parseContext();
        parseMethod();
        if(!"/".equals(context.getPath())) {
            uri = StrUtil.removePrefix(uri, context.getPath()); // 修正uri
            if (StrUtil.isEmpty(uri)) {
                uri = "/";
            }
        }
        parseParameters();
        parseHeaders();
//        System.out.println(headerMap);
        parseCookies();
    }

    private void parseParameters() { // 获取参数，GET的参数放在uri里，POST参数放在请求最后的请求体里
        if ("GET".equals(this.getMethod())) {
            String url = StrUtil.subBetween(requestString, " ", " ");
            if (StrUtil.contains(url, '?')) {
                queryString = StrUtil.subAfter(url, '?', false);
            }
        }
        if ("POST".equals(this.getMethod())) {
            queryString = StrUtil.subAfter(requestString, "\r\n\r\n", false);
        }
        if (null == queryString)
            return;
        queryString = URLUtil.decode(queryString);
        String[] parameterValues = queryString.split("&");
        for (String parameterValue : parameterValues) {
            String[] nameValues = parameterValue.split("=");
            String name = nameValues[0];
            String value = nameValues[1];
            String[] values = parameterMap.get(name);
            if (null == values) {
                values = new String[] { value };
                parameterMap.put(name, values);
            } else {
                values = ArrayUtil.append(values, value);
                parameterMap.put(name, values);
            }
        }
    }

    public void parseHeaders() {
        StringReader stringReader = new StringReader(requestString);
        List<String> lines = new ArrayList<>();
        IoUtil.readLines(stringReader, lines);
        for (int i = 1; i < lines.size(); i++) { // i = 1 绕过请求头
            String line = lines.get(i);
            if (0 == line.length()) // 绕过请求体
                break;
            String[] segs = line.split(":");
            String headerName = segs[0].toLowerCase();
            String headerValue = segs[1];
            headerMap.put(headerName, headerValue);
            // System.out.println(line);
        }
    }

    private void parseMethod() {
        method = StrUtil.subBefore(requestString, " ", false);
    }

    private void parseContext() {
        Engine engine = connector.getService().getEngine();
        context = engine.getDefaultHost().getContext(uri);
        if (null != context) {
            return;
        }
        String path = StrUtil.subBetween(uri, "/", "/");
        if (null == path)
            path = "/";
        else
            path = "/" + path;
        context = engine.getDefaultHost().getContext(path);
        if (null == context)
            context = engine.getDefaultHost().getContext("/"); // Context对象找不到，则返回默认的ROOT Context
    }

    private void parseHttpRequest() throws IOException {
        InputStream is = socket.getInputStream();
        byte[] bytes = MiniBrowser.readBytes(is, false);
        requestString = new String(bytes, StandardCharsets.UTF_8);
    }

    private void parseCookies() {
        List<Cookie> cookieList = new ArrayList<>();
        String cookies = headerMap.get("cookie");
        if (null != cookies) {
            String[] pairs = StrUtil.split(cookies, ";");
            for (String pair : pairs) {
                if (StrUtil.isBlank(pair))
                    continue;
                // System.out.println(pair.length());
                // System.out.println("pair:"+pair);
                String[] segs = StrUtil.split(pair, "=");
                String name = segs[0].trim();
                String value = segs[1].trim();
                Cookie cookie = new Cookie(name, value);
                cookieList.add(cookie);
            }
        }
        this.cookies = ArrayUtil.toArray(cookieList, Cookie.class);
    }

    private void parseUri() {
        String temp;
        // 请求的uri就是刚开始的两个空格之间的数据
        temp = StrUtil.subBetween(requestString, " ", " ");
        System.out.println(temp);
        // 判断是否有参数
        if (!StrUtil.contains(temp, '?')) {
            uri = temp;
            return;
        }
        temp = StrUtil.subBefore(temp, '?', false);
        uri = temp;
    }

    public Socket getSocket() {
        return socket;
    }
    public String getUri() {
        return uri;
    }
    public void setUri(String uri) {
        this.uri = uri;
    }
    public String getRequestString() {
        return requestString;
    }
    public Connector getConnector() {
        return connector;
    }
    public Context getContext() {
        return context;
    }
    @Override
    public ServletContext getServletContext() {
        return context.getServletContext();
    }
    @Override
    public String getRealPath(String path) {
        return getServletContext().getRealPath(path);
    }
    @Override
    public String getMethod() {
        return method;
    }
    @Override
    public String getParameter(String name) {
        String[] values = parameterMap.get(name);
        if (null != values && 0 != values.length)
            return values[0];
        return null;
    }
    @Override
    public Map<String, String[]> getParameterMap() {
        return parameterMap;
    }
    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }
    @Override
    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }
    @Override
    public String getHeader(String name) {
        if(null==name)
            return null;
        name = name.toLowerCase();
        return headerMap.get(name);
    }
    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> keys = headerMap.keySet();
        return Collections.enumeration(keys);
    }
    @Override
    public int getIntHeader(String name) {
        String value = headerMap.get(name);
        return Convert.toInt(value, 0);
    }
    @Override
    public String getLocalAddr() {
        return socket.getLocalAddress().getHostAddress();
    }
    @Override
    public String getLocalName() {
        return socket.getLocalAddress().getHostName();
    }
    @Override
    public int getLocalPort() {
        return socket.getLocalPort();
    }
    @Override
    public String getProtocol() {
        return "HTTP:/1.1";
    }
    @Override
    public String getRemoteAddr() {
        InetSocketAddress isa = (InetSocketAddress) socket.getRemoteSocketAddress();
        String temp = isa.getAddress().toString();
        return StrUtil.subAfter(temp, "/", false);
    }
    @Override
    public String getRemoteHost() {
        InetSocketAddress isa = (InetSocketAddress) socket.getRemoteSocketAddress();
        return isa.getHostName();
    }
    @Override
    public int getRemotePort() {
        return socket.getPort();
    }
    @Override
    public String getScheme() {
        return "http";
    }
    @Override
    public String getServerName() {
        return getHeader("host").trim();
    }
    @Override
    public int getServerPort() {
        return getLocalPort();
    }
    @Override
    public String getContextPath() {
        String result = this.context.getPath();
        if ("/".equals(result))
            return "";
        return result;
    }
    @Override
    public String getRequestURI() {
        return uri;
    }
    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0) {
            port = 80; // Work around java.net.URL bug
        }
        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80)) || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());
        return url;
    }
    @Override
    public String getServletPath() {
        return uri;
    }
    @Override
    public Cookie[] getCookies() {
        return cookies;
    }
    // 从 cookie 中获取 sessionId
    public String getJSessionIdFromCookie() {
        if (null == cookies)
            return null;
        for (Cookie cookie : cookies) {
            if ("JSESSIONID".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
    @Override
    public HttpSession getSession() {
        return session;
    }
    public void setSession(HttpSession session) {
        this.session = session;
    }
    public boolean isForwarded() {
        return forwarded;
    }
    public void setForwarded(boolean forwarded) {
        this.forwarded = forwarded;
    }
    @Override
    public RequestDispatcher getRequestDispatcher(String uri) {
        return new ApplicationRequestDispatcher(uri);
    }
    @Override
    public void removeAttribute(String name) {
        attributesMap.remove(name);
    }
    @Override
    public void setAttribute(String name, Object value) {
        attributesMap.put(name, value);
    }
    @Override
    public Object getAttribute(String name) {
        return attributesMap.get(name);
    }
    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> keys = attributesMap.keySet();
        return Collections.enumeration(keys);
    }
}
