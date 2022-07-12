package cn.how2j.diytomcat.test;

import cn.how2j.diytomcat.util.MiniBrowser;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.NetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestTomcat {

    // 默认端口和ip
    private static int port = 18080;
    private static String ip = "127.0.0.1";

    @BeforeClass
    public static void beforeClass() {
        // 所有测试开始前看diy tomcat是否已经启动了
        if (NetUtil.isUsableLocalPort(port)) { // 检查端口是否启动可能会产生空消息
            System.err.println("请先启动 位于端口：" + port + " 的diy tomcat，否则无法进入单元测试");
            System.exit(1);
        }
        else {
            System.out.println("检测到diy tomcat已经启动，开始进行单元测试");
        }
    }

    @Test
    public void testHelloTomcat() { // 测试请求
        String html = getContentString("/");
        Assert.assertEquals(html, "Hello DIY Tomcat from how2j.cn");
    }
    @Test
    public void testaHtml() { // 测试请求文本文件
        String html = getContentString("/a.html");
        Assert.assertEquals(html, "Hello DIY Tomcat from a.html");
    }
    @Test
    public void testTimeConsumeHtml() throws InterruptedException { // 测试耗时任务
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(20, 20, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(10)); // 创建包含20个线程的线程池
        TimeInterval timeInterval = DateUtil.timer(); // 开始计时

        for(int i = 0; i<3; i++){
            threadPool.execute(new Runnable(){
                public void run() {
                    getContentString("/timeConsume.html");
                }
            });
        }
        threadPool.shutdown(); // 尝试关闭线程池，如果其中有任务在运行，就不会强制关闭
        threadPool.awaitTermination(1, TimeUnit.HOURS); // 若一小时后仍在运行任务，强制关闭线程池

        long duration = timeInterval.intervalMs(); // 获取耗时时长

        Assert.assertTrue(duration < 3000);
    }
    @Test
    public void testaIndex() { // 测试欢迎页
        String html = getContentString("/a");
        Assert.assertEquals(html,"Hello DIY Tomcat from index.html@a");
    }
    @Test
    public void testbIndex() {
        String html = getContentString("/b/");
        Assert.assertEquals(html,"Hello DIY Tomcat from index.html@b");
    }
    @Test
    public void testaTxt() {
        String response  = getHttpString("/a.txt");
        containAssert(response, "Content-Type: text/plain");
    }
    @Test
    public void testPNG() { // 测试二进制文件
        byte[] bytes = getContentBytes("/logo.png");
        int pngFileLength = 1672;
        Assert.assertEquals(pngFileLength, bytes.length);
    }
    @Test
    public void testPDF() {
        byte[] bytes = getContentBytes("/etf.pdf");
        int pngFileLength = 3590775;
        Assert.assertEquals(pngFileLength, bytes.length);
    }
    @Test
    public void test404() { // 测试404
        String response  = getHttpString("/not_exist.html");
        containAssert(response, "HTTP/1.1 404 Not Found");
    }
    @Test
    public void test500() { // 测试500
        String response  = getHttpString("/500.html");
        containAssert(response, "HTTP/1.1 500 Internal Server Error");
    }
    @Test
    public void testhello() {
        String html = getContentString("/j2ee/hello");
        Assert.assertEquals(html,"Hello DIY Tomcat from HelloServlet");
    }
    @Test
    public void testJavawebHello() {
        String html = getContentString("/javaweb/hello");
        containAssert(html,"Hello DIY Tomcat from HelloServlet@javaweb");
    }
    @Test
    public void testgetParam() {
        String uri = "/javaweb/param";
        String url = StrUtil.format("http://{}:{}{}", ip,port,uri);
        Map<String,Object> params = new HashMap<>();
        params.put("name","meepo");
        String html = MiniBrowser.getContentString(url, params, true);
        Assert.assertEquals(html,"get name:meepo");
    }
    @Test
    public void testpostParam() {
        String uri = "/javaweb/param";
        String url = StrUtil.format("http://{}:{}{}", ip,port,uri);
        Map<String,Object> params = new HashMap<>();
        params.put("name","meepo");
        String html = MiniBrowser.getContentString(url, params, false);
        Assert.assertEquals(html,"post name:meepo");
    }
    @Test
    public void testheader() {
        String html = getContentString("/javaweb/header");
        Assert.assertEquals(html,"how2j mini brower / java1.8");
    }
    @Test
    public void testsetCookie() {
        String html = getHttpString("/javaweb/setCookie");
        containAssert(html,"Set-Cookie: name=Gareen(cookie); Expires=");
    }
    // 自带浏览器未实现cookie功能
    @Test
    public void testgetCookie() throws IOException {
        String url = StrUtil.format("http://{}:{}{}", ip,port,"/javaweb/getCookie");
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestProperty("Cookie","name=Gareen(cookie)");
        conn.connect();
        InputStream is = conn.getInputStream();
        String html = IoUtil.read(is, "utf-8");
        containAssert(html,"name:Gareen(cookie)");
    }
    // 先通过访问 setSession，设置 name_in_session, 并且得到 jsessionid,
    // 然后 把 jsessionid 作为 Cookie 的值提交到 getSession，就获取了session 中的数据了
    @Test
    public void testSession() throws IOException {
        String jsessionid = getContentString("/javaweb/setSession");
        if(null!=jsessionid)
            jsessionid = jsessionid.trim();
        String url = StrUtil.format("http://{}:{}{}", ip,port,"/javaweb/getSession");
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestProperty("Cookie","JSESSIONID="+jsessionid);
        conn.connect();
        InputStream is = conn.getInputStream();
        String html = IoUtil.read(is, "utf-8");
        containAssert(html,"Gareen(session)");
    }
    @Test
    public void testGzip() {
        byte[] gzipContent = getContentBytes("/",true);
        byte[] unGzipContent = ZipUtil.unGzip(gzipContent);
        String html = new String(unGzipContent);
        Assert.assertEquals(html, "Hello DIY Tomcat from how2j.cn");
    }
    @Test
    public void testJsp() {
        String html = getContentString("/javaweb/");
        Assert.assertEquals(html, "hello jsp@javaweb");
    }
    @Test
    public void testClientJump(){
        String http_servlet = getHttpString("/javaweb/jump1");
        containAssert(http_servlet,"HTTP/1.1 302 Found");
        String http_jsp = getHttpString("/javaweb/jump1.jsp");
        containAssert(http_jsp,"HTTP/1.1 302 Found");
    }
    @Test
    public void testServerJump(){
        String http_servlet = getHttpString("/javaweb/jump2");
        containAssert(http_servlet,"Hello DIY Tomcat from HelloServlet");
    }
    @Test
    public void testServerJumpWithAttributes(){
        String http_servlet = getHttpString("/javaweb/jump2");
        containAssert(http_servlet,"Hello DIY Tomcat from HelloServlet@javaweb, the name is gareen");
    }
    @Test
    public void testJavaweb0Hello() {
        String html = getContentString("/javaweb0/hello");
        containAssert(html,"Hello DIY Tomcat from HelloServlet@javaweb");
    }

    private void containAssert(String html, String string) { // 包含式测试
        boolean match = StrUtil.containsAny(html, string);
        Assert.assertTrue(match);
    }

    // 获取响应内容
    private String getContentString(String uri) {
        String url = StrUtil.format("http://{}:{}{}", ip, port, uri);
        return MiniBrowser.getContentString(url);
    }

    private String getHttpString(String uri) {
        String url = StrUtil.format("http://{}:{}{}", ip,port,uri);
        return MiniBrowser.getHttpString(url);
    }

    private byte[] getContentBytes(String uri) {
        return getContentBytes(uri,false);
    }

    private byte[] getContentBytes(String uri,boolean gzip) {
        String url = StrUtil.format("http://{}:{}{}", ip,port,uri);
        return MiniBrowser.getContentBytes(url,gzip);
    }
}
