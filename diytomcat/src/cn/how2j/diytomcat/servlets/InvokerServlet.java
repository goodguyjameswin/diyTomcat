package cn.how2j.diytomcat.servlets;

import cn.how2j.diytomcat.catalina.Context;
import cn.how2j.diytomcat.http.Request;
import cn.how2j.diytomcat.http.Response;
import cn.how2j.diytomcat.util.Constant;
import cn.hutool.core.util.ReflectUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class InvokerServlet extends HttpServlet {

    private static InvokerServlet instance = new InvokerServlet(); // 饿汉式单例模式

    public static InvokerServlet getInstance() {
        return instance;
    }

    private InvokerServlet() {}

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws IOException, ServletException {
        Request request = (Request) httpServletRequest;
        Response response = (Response) httpServletResponse;

        String uri = request.getUri();
        Context context = request.getContext();
        String servletClassName = context.getServletClassName(uri);
        try {
            Class<?> servletClass = context.getWebappClassLoader().loadClass(servletClassName);
//            System.out.println("servletClass:" + servletClass);
//            System.out.println("servletClass'classLoader:" + servletClass.getClassLoader());
            Object servletObject = context.getServlet(servletClass); // 获取sevlet单例
            // servlet实现了HttpServlet，所以一定提供了service方法，会根据request的Method，访问其对应的doGet和doPost方法
            ReflectUtil.invoke(servletObject, "service", request, response);
            // 请求处理成功
            if(null!=response.getRedirectPath())
                response.setStatus(Constant.CODE_302);
            else
                response.setStatus(Constant.CODE_200);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
