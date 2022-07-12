package cn.how2j.diytomcat.http;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Response extends BaseResponse {

    private StringWriter stringWriter;
    private PrintWriter writer;
    private String contentType;
    private byte[] body;
    private int status;
    private List<Cookie> cookies;
    private String redirectPath; // 保存客户端跳转路径（默认是302临时跳转）

    public Response(){
        this.stringWriter = new StringWriter(); // 用于存放返回的html文本
        // response.getWriter().println() 写进去的数据最后都写到stringWriter里面去了
        this.writer = new PrintWriter(stringWriter);
        this.contentType = "text/html"; // 对应响应头里面的Content-type
        this.cookies = new ArrayList<>();
    }

    public String getCookiesHeader() { // Cookies 集合转换成 cookie Header
        if(null==cookies)
            return "";
        String pattern = "EEE, d MMM yyyy HH:mm:ss 'GMT'";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : getCookies()) {
            sb.append("\r\n");
            sb.append("Set-Cookie: ");
            sb.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
            if (-1 != cookie.getMaxAge()) { //-1 mean forever
                sb.append("Expires=");
                Date now = new Date();
                Date expire = DateUtil.offset(now, DateField.MINUTE, cookie.getMaxAge());
                sb.append(sdf.format(expire));
                sb.append("; ");
            }
            if (null != cookie.getPath()) {
                sb.append("Path=").append(cookie.getPath());
            }
        }
        return sb.toString();
    }

    @Override
    public PrintWriter getWriter() {
        return writer;
    }

    public byte[] getBody() throws UnsupportedEncodingException {
        if (null == body) {
            String content = stringWriter.toString();
            return content.getBytes(StandardCharsets.UTF_8);
        }
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    @Override
    public String getContentType() {
        return contentType;
    }
    @Override
    public void setContentType(String contentType) { this.contentType = contentType; }
    @Override
    public void setStatus(int status) {
        this.status = status;
    }
    @Override
    public int getStatus() {
        return status;
    }
    @Override
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }
    public List<Cookie> getCookies() {
        return this.cookies;
    }
    public String getRedirectPath() {
        return this.redirectPath;
    }
    @Override
    public void sendRedirect(String redirect) throws IOException {
        this.redirectPath = redirect;
    }
}
