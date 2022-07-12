package cn.how2j.diytomcat.http;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.*;

public class StandardSession implements HttpSession {
    private Map<String, Object> attributesMap;

    private String id; // 当前 sesssion 的唯一id
    private long creationTime; // 创建时间
    private long lastAccessedTime; // 最后一次访问时间
    private ServletContext servletContext;
    private int maxInactiveInterval; // 最大持续时间的分钟数

    public StandardSession(String jsessionid, ServletContext servletContext) {
        this.attributesMap = new HashMap<>();
        this.id = jsessionid;
        this.creationTime = System.currentTimeMillis();
        this.servletContext = servletContext;
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
    @Override
    public long getCreationTime() {
        return this.creationTime;
    }
    @Override
    public String getId() {
        return id;
    }
    @Override
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }
    @Override
    public int getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }
    @Override
    public void setMaxInactiveInterval(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }
    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }
    @Override
    public HttpSessionContext getSessionContext() {
        return null;
    }
    @Override
    public Object getValue(String arg0) {
        return null;
    }
    @Override
    public String[] getValueNames() {

        return null;
    }
    @Override
    public void invalidate() {
        attributesMap.clear();
    }
    @Override
    public boolean isNew() {
        return creationTime == lastAccessedTime;
    }
    @Override
    public void putValue(String arg0, Object arg1) {
    }
    @Override
    public void removeValue(String arg0) {
    }

    public void setLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }
}
