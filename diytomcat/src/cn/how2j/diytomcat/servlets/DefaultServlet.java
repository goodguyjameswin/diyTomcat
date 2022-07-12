package cn.how2j.diytomcat.servlets;

import cn.how2j.diytomcat.catalina.Context;
import cn.how2j.diytomcat.http.Request;
import cn.how2j.diytomcat.http.Response;
import cn.how2j.diytomcat.util.Constant;
import cn.how2j.diytomcat.util.WebXMLUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class DefaultServlet extends HttpServlet {

    private static DefaultServlet instance = new DefaultServlet(); // 饿汉式单例模式

    public static DefaultServlet getInstance() {
        return instance;
    }

    private DefaultServlet() {}

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws IOException, ServletException {
        // 处理静态资源
        Request request = (Request) httpServletRequest;
        Response response = (Response) httpServletResponse;
        Context context = request.getContext();

        String uri = request.getUri();
        if("/500.html".equals(uri)) {
            throw new RuntimeException("this is a deliberately created exception");
        }
        if("/".equals(uri)){
            uri = WebXMLUtil.getWelcomeFile(context); // 默认访问欢迎页
        }
        if(uri.endsWith(".jsp")){ // 如果welcome文件是jsp文件，就交由JspServlet来处理
            JspServlet.getInstance().service(request, response);
            return;
        }
        System.out.println("uri:" + uri);
        String fileName = StrUtil.removePrefix(uri, "/");
        File file = FileUtil.file(request.getRealPath(fileName));

        if(file.exists()){
            String extName = FileUtil.extName(file);
            String mimeType = WebXMLUtil.getMimeType(extName);
            response.setContentType(mimeType); // 根据请求文件类型，设置响应头
//            String fileContent = FileUtil.readUtf8String(file);
//            response.getWriter().println(fileContent); // 将文本文件读取成字符串
            byte[] body = FileUtil.readBytes(file);
            response.setBody(body); // 读取二进制文件
            if(fileName.equals("timeConsume.html")){
                ThreadUtil.sleep(1000);
            }
            response.setStatus(Constant.CODE_200); // 设置响应代码
        } else {
            response.setStatus(Constant.CODE_404);
        }

    }

}
