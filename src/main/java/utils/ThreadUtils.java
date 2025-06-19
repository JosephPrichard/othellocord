package utils;

import java.util.concurrent.ThreadFactory;

public class ThreadUtils {
    public static final int CORES = Runtime.getRuntime().availableProcessors();

    public static ThreadFactory createThreadFactory(String pool) {
        return (task) -> {
            Thread thread = new Thread(task, pool);
            thread.setDaemon(true);
            return thread;
        };
    }
}
