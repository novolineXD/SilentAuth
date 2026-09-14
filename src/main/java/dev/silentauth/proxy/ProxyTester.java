package dev.silentauth.proxy;

import dev.silentauth.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProxyTester {

    private static final String PROBE_HOST = "sessionserver.mojang.com";
    private static final int PROBE_PORT = 443;
    private static final int TIMEOUT_MS = 8000;

    private final ExecutorService pool;

    public ProxyTester() {
        this.pool = Executors.newFixedThreadPool(8, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "SilentAuth-ProxyTest-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void testAsync(final ProxyEntry entry, final Runnable onFinished) {
        if (entry == null) {
            return;
        }
        pool.submit(new Runnable() {
            @Override
            public void run() {
                test(entry);
                if (onFinished != null) {
                    onFinished.run();
                }
            }
        });
    }

    public void testAll(List<ProxyEntry> entries, Runnable onFinished) {
        for (ProxyEntry entry : entries) {
            testAsync(entry, onFinished);
        }
    }

    public void test(ProxyEntry entry) {
        long start = System.currentTimeMillis();
        Socket socket = null;
        try {
            socket = ProxySockets.open(entry, PROBE_HOST, PROBE_PORT, TIMEOUT_MS);
            entry.setLatencyMs((int) (System.currentTimeMillis() - start));
            entry.setLastError("");
        } catch (IOException e) {
            entry.setLatencyMs(ProxyEntry.UNREACHABLE);
            entry.setLastError(shorten(e.getMessage()));
            Log.warn("Proxy " + entry.describe() + " failed: " + e.getMessage());
        } finally {
            ProxySockets.closeQuietly(socket);
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private static String shorten(String message) {
        if (message == null || message.isEmpty()) {
            return "failed";
        }
        return message.length() > 28 ? message.substring(0, 28) : message;
    }
}
