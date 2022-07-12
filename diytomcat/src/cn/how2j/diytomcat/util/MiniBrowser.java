package cn.how2j.diytomcat.util;

import cn.hutool.http.HttpUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MiniBrowser {

    public static void main(String[] args) {
        String url = "http://static.how2j.cn/diytomcat.html";
        String contentString = getContentString(url, false);
        System.out.println(contentString);
        String httpString = getHttpString(url, false);
        System.out.println(httpString);
    }

    public static byte[] getContentBytes(String url, Map<String,Object> params, boolean isGet) {
        return getContentBytes(url, false, params, isGet);
    }

    public static byte[] getContentBytes(String url, boolean gzip) {
        return getContentBytes(url, gzip,null,true);
    }

    public static byte[] getContentBytes(String url) {
        return getContentBytes(url, false, null, true);
    }

    public static String getContentString(String url, Map<String, Object> params, boolean isGet) {
        return getContentString(url, false, params, isGet);
    }

    public static String getContentString(String url, boolean gzip) {
        return getContentString(url, gzip, null, true);
    }

    public static String getContentString(String url) {
        return getContentString(url, false, null, true);
    }

    public static String getContentString(String url, boolean gzip, Map<String,Object> params, boolean isGet) {
        byte[] result = getContentBytes(url, gzip, params, isGet);
        if(null==result)
            return null;
        return new String(result, StandardCharsets.UTF_8).trim(); // 去掉首位换行符
    }

    // 返回二进制的http响应内容（去掉头的html部分）
    public static byte[] getContentBytes(String url, boolean gzip, Map<String,Object> params, boolean isGet) {
        byte[] response = getHttpBytes(url, gzip, params, isGet);
        byte[] doubleReturn = "\r\n\r\n".getBytes();

        int pos = -1;
        for (int i = 0; i < response.length-doubleReturn.length; i++) {
            byte[] temp = Arrays.copyOfRange(response, i, i + doubleReturn.length);

            if(Arrays.equals(temp, doubleReturn)) {
                pos = i;
                break;
            }
        }
        if(-1==pos)
            return null;

        pos += doubleReturn.length;

        return Arrays.copyOfRange(response, pos, response.length);
    }

    public static String getHttpString(String url,boolean gzip, Map<String,Object> params, boolean isGet) {
        byte[]  bytes=getHttpBytes(url,gzip,params,isGet);
        return new String(bytes).trim();
    }

    public static String getHttpString(String url,boolean gzip) {
        return getHttpString(url, gzip, null, true);
    }

    public static String getHttpString(String url) {
        return getHttpString(url, false, null, true);
    }

    public static byte[] getHttpBytes(String url,boolean gzip, Map<String,Object> params, boolean isGet) {
        String method = isGet?"GET":"POST";
        // 返回二进制的http响应
        byte[] result = null;
        try {
            URL u = new URL(url);
            Socket client = new Socket();
            int port = u.getPort();
            if(-1 == port)
                port = 80;
            InetSocketAddress inetSocketAddress = new InetSocketAddress(u.getHost(), port);
            client.connect(inetSocketAddress, 1000); // 浏览器（客户端）与服务端建立连接
            Map<String,String> requestHeaders = new HashMap<>();

            requestHeaders.put("Host", u.getHost() + ":" + port);
            requestHeaders.put("Accept", "text/html");
            requestHeaders.put("Connection", "close");
            requestHeaders.put("User-Agent", "how2j mini brower / java1.8");

            if(gzip)
                requestHeaders.put("Accept-Encoding", "gzip");

            String path = u.getPath();
            if(path.length()==0)
                path = "/";

            if(null!=params && isGet){ // GET带参请求
                String paramsString = HttpUtil.toParams(params);
                path = path + "?" + paramsString;
            }

            String firstLine = method + " " + path + " HTTP/1.1\r\n";

            StringBuffer httpRequestString = new StringBuffer();
            httpRequestString.append(firstLine);
            Set<String> headers = requestHeaders.keySet();
            for (String header : headers) {
                String headerLine = header + ":" + requestHeaders.get(header)+"\r\n";
                httpRequestString.append(headerLine);
            }

            if(null!=params && !isGet){ // POST带参请求
                String paramsString = HttpUtil.toParams(params);
                httpRequestString.append("\r\n");
                httpRequestString.append(paramsString);
            }

            PrintWriter pWriter = new PrintWriter(client.getOutputStream(), true);
            pWriter.println(httpRequestString); // 浏览器发送请求
            InputStream is = client.getInputStream(); // 接受回传数据

            result = readBytes(is, true);
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
            result = e.toString().getBytes(StandardCharsets.UTF_8);
        }

        return result;

    }

    public static byte[] readBytes(InputStream is, boolean fully) throws IOException {
        // 服务端回传过来的信息很有可能长度不是刚好1024，如果超过或者低于都会存在信息丢失，故采用循环读取
        int buffer_size = 1024;
        byte[] buffer = new byte[buffer_size];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while(true) {
            int length = is.read(buffer);
            if(-1==length)
                break;
            baos.write(buffer, 0, length);
            if(!fully && length!=buffer_size)
                break;
        }
        return baos.toByteArray();
    }
}
