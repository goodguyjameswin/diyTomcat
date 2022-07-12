package cn.how2j.diytomcat.catalina;

import cn.how2j.diytomcat.http.Request;
import cn.how2j.diytomcat.http.Response;
import cn.how2j.diytomcat.util.ThreadPoolUtil;

import cn.hutool.log.LogFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Connector implements Runnable {

    int port;
    private Service service;
    private String compression;
    private int compressionMinSize;
    private String noCompressionUserAgents;
    private String compressableMimeType;

    public Connector(Service service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
//            if (!NetUtil.isUsableLocalPort(port)) {
//                System.out.println(port +
//                        " 端口已经被占用了，排查并关闭本端口的办法请用：" +
//                        "\r\nhttps://how2j.cn/k/tomcat/tomcat-portfix/545.html");
//                return;
//            }
            ServerSocket ss = new ServerSocket(port);

            while (true) { // 外套循环，表示处理掉一个Socket连接请求之后，再处理下一个连接请求
                Socket s = ss.accept(); //  表示收到浏览器客户端的请求
                Runnable r = () -> {
                    try { // 根据请求创建 request 和 response 对象，然后交给 HttpProcessor 处理
                        Request request = new Request(s, Connector.this);
                        Response response = new Response();
                        HttpProcessor processor = new HttpProcessor();
                        processor.execute(s,request, response);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (!s.isClosed())
                            try {
                                s.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    }
                };
                ThreadPoolUtil.run(r);
            }
        } catch (IOException e) {
            LogFactory.get().error(e);
            e.printStackTrace();
        }
    }

    public void init() {
        LogFactory.get().info("Initializing ProtocolHandle [http-bio-{}]", port);
    }

    public void start() { // 创建一个线程，以当前类为任务，启动运行，并打印 tomcat 风格的日志
        LogFactory.get().info("Starting ProtocolHandler [http-bio-{}]", port);
        new Thread(this).start();
    }

    public Service getService() {
        return service;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }

    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents;
    }

    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        this.noCompressionUserAgents = noCompressionUserAgents;
    }

    public String getCompressableMimeType() {
        return compressableMimeType;
    }

    public void setCompressableMimeType(String compressableMimeType) {
        this.compressableMimeType = compressableMimeType;
    }
}
