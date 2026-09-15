package dev.silentauth.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Async {

    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "SilentAuth-Worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private Async() {
    }

    public static void run(final Runnable task) {
        POOL.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    Log.error("Background task failed", t);
                }
            }
        });
    }
}
