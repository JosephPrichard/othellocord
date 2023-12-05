/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

import java.util.concurrent.ThreadFactory;

public class Threading {

    public static final int CORES = Runtime.getRuntime().availableProcessors();

    public static ThreadFactory createThreadFactory(String pool) {
        return (task) -> {
            Thread thread = new Thread(task, pool);
            thread.setDaemon(true);
            return thread;
        };
    }
}
