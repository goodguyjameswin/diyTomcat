package cn.how2j.diytomcat.http;

import cn.how2j.diytomcat.catalina.HttpProcessor;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

public class ApplicationRequestDispatcher implements RequestDispatcher { // 实现 RequestDispatcher 接口，用于进行服务端跳转

    private String uri;

    public ApplicationRequestDispatcher(String uri) {
        if (!uri.startsWith("/"))
            uri = "/" + uri;
        this.uri = uri;
    }

    @Override
    public void forward(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        Request request = (Request) servletRequest;
        Response response = (Response) servletResponse;
        request.setUri(uri); // 修改 request 的 uri
        HttpProcessor processor = new HttpProcessor();
        processor.execute(request.getSocket(), request, response); // 通过 HttpProcessor 的 execute 再执行一次
        request.setForwarded(true); // 相当于在服务器内部再次访问了某个页面
    }

    @Override
    public void include(ServletRequest arg0, ServletResponse arg1) throws ServletException, IOException {
        // TODO Auto-generated method stub

    }
}
