package cn.how2j.diytomcat.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolUtil {
    // 当核心线程占满后，不会立即分配新的线程处理更多的请求，
    // 而是把这些请求放在LinkedBlockingQueue里，等待核心线程空闲，当队列中请求占满后才会增加更多的线程
    private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            20, 100, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(10));
    public static void run(Runnable r) {
        threadPool.execute(r);
    }
}
