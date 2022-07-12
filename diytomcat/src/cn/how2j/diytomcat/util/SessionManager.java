package cn.how2j.diytomcat.util;

import cn.how2j.diytomcat.http.Request;
import cn.how2j.diytomcat.http.Response;
import cn.how2j.diytomcat.http.StandardSession;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

public class SessionManager {
    private static Map<String, StandardSession> sessionMap = new HashMap<>();
    private static int defaultTimeout = getTimeout();
    static {
        startSessionOutdateCheckThread();
    }

    public static HttpSession getSession(String jsessionid, Request request, Response response) {

        if (null == jsessionid) { // 如果浏览器没有传递 jsessionid 过来，那么就创建一个新的session
            return newSession(request, response);
        } else {
            StandardSession currentSession = sessionMap.get(jsessionid);
            if (null == currentSession) { // 如果浏览器传递过来的 jsessionid 无效，那么也创建一个新的 sessionid
                return newSession(request, response);
            } else { // 否则就使用现成的session, 并且修改它的lastAccessedTime， 以及创建对应的 cookie
                currentSession.setLastAccessedTime(System.currentTimeMillis());
                createCookieBySession(currentSession, request, response);
                return currentSession;
            }
        }
    }

    private static void createCookieBySession(HttpSession session, Request request, Response response) {
        Cookie cookie = new Cookie("JSESSIONID", session.getId());
        cookie.setMaxAge(session.getMaxInactiveInterval());
        cookie.setPath(request.getContext().getPath());
        response.addCookie(cookie);
    }

    private static HttpSession newSession(Request request, Response response) {
        ServletContext servletContext = request.getServletContext();
        String sid = generateSessionId();
        StandardSession session = new StandardSession(sid, servletContext);
        session.setMaxInactiveInterval(defaultTimeout);
        sessionMap.put(sid, session);
        createCookieBySession(session, request, response);
        return session;
    }

    private static int getTimeout() { // 获取session的默认失效时间
        int defaultResult = 30;
        try {
            Document d = Jsoup.parse(Constant.webXmlFile, "utf-8");
            Elements es = d.select("session-config session-timeout");
            if (es.isEmpty())
                return defaultResult;
            return Convert.toInt(es.get(0).text());
        } catch (IOException e) {
            return defaultResult;
        }
    }
    // 从sessionMap里根据 lastAccessedTime 筛选出过期的 jsessionids ,然后把它们从 sessionMap 里去掉
    private static void checkOutDateSession() {
        Set<String> jsessionids = sessionMap.keySet();
        List<String> outdateJessionIds = new ArrayList<>();

        for (String jsessionid : jsessionids) {
            StandardSession session = sessionMap.get(jsessionid);
            long interval = System.currentTimeMillis() -  session.getLastAccessedTime();
            if (interval > session.getMaxInactiveInterval() * 1000L)
                outdateJessionIds.add(jsessionid);
        }

        for (String jsessionid : outdateJessionIds) {
            sessionMap.remove(jsessionid);
        }
    }

    private static void startSessionOutdateCheckThread() {
        new Thread(() -> {
            while (true) {
                checkOutDateSession();
                ThreadUtil.sleep(1000 * 30); // 每隔30秒调用一次 checkOutDateSession 方法
            }
        }).start();
    }

    public static synchronized String generateSessionId() { // 创建 sessionId
        String result = null;
        byte[] bytes = RandomUtil.randomBytes(16);
        result = new String(bytes);
        result = SecureUtil.md5(result);
        result = result.toUpperCase();
        return result;
    }

}