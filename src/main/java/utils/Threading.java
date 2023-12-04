/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class Threading {

    public static final ExecutorService CPU_BND_EXECUTOR
        = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), createThreadFactory("CPU-Pool"));
    public static final ExecutorService IO_TASK_EXECUTOR
        = Executors.newCachedThreadPool(createThreadFactory("IO-Pool"));

    public static ThreadFactory createThreadFactory(String pool) {
        return (task) -> {
            Thread thread = new Thread(task, pool);
            thread.setDaemon(true);
            return thread;
        };
    }
}
