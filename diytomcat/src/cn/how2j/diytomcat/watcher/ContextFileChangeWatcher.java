package cn.how2j.diytomcat.watcher;

import cn.how2j.diytomcat.catalina.Context;
import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.core.io.watch.WatchUtil;
import cn.hutool.core.io.watch.Watcher;
import cn.hutool.log.LogFactory;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

public class ContextFileChangeWatcher {

    private WatchMonitor monitor; // 真正作用的监听器
    private boolean stop = false; // 标记是否已经暂停

    public ContextFileChangeWatcher(Context context) {
        // 通过WatchUtil.createAll创建监听器；
        // context.getDocBase()代表监听的文件夹；
        // Integer.MAX_VALUE代表监听的深入，如果是0或者1，就表示只监听当前目录，而不监听子目录；
        // new Watcher 当有文件发生变化，那么就会访问 Watcher 对应的方法
        this.monitor = WatchUtil.createAll(context.getDocBase(), Integer.MAX_VALUE, new Watcher() {

            private void dealWith(WatchEvent<?> event) { // 统一处理（重新加载context）
                synchronized (ContextFileChangeWatcher.class) { // 多线程下同步，防止context重载多次
                    String fileName = event.context().toString();
                    if (stop) // 表示已经重载过了，通知后续消息不需要再进行处理
                        return;
                    // 只应对 jar class 和 xml 发生的变化，其他的不需要重启
                    if (fileName.endsWith(".jar") || fileName.endsWith(".class") || fileName.endsWith(".xml")) {
                        stop = true;
                        LogFactory.get().info(ContextFileChangeWatcher.this + " 检测到了Web应用下的重要文件变化 {} " , fileName);
                        context.reload();
                    }

                }
            }
            @Override
            public void onCreate(WatchEvent<?> event, Path currentPath) {
                dealWith(event);
            }
            @Override
            public void onModify(WatchEvent<?> event, Path currentPath) {
                dealWith(event);
            }
            @Override
            public void onDelete(WatchEvent<?> event, Path currentPath) {
                dealWith(event);
            }
            @Override
            public void onOverflow(WatchEvent<?> event, Path currentPath) {
                dealWith(event);
            }

        });
        this.monitor.setDaemon(true);
    }

    public void start() {
        monitor.start();
    }

    public void stop() {
        monitor.close();
    }
}
