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
    private static final long FRESH_FOR_MS = 5L * 60L * 1000L;

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

    /** Tests one proxy. Returns at once. */
    public void testAsync(final ProxyEntry entry) {
        if (entry == null || entry.isBeingTested()) {
            return;
        }
        entry.setLatencyMs(ProxyEntry.TESTING);
        pool.submit(new Runnable() {
            @Override
            public void run() {
                test(entry);
            }
        });
    }

    /** Tests everything that has not been tested recently, so nothing needs a manual check. */
    public void testStale(List<ProxyEntry> entries) {
        for (ProxyEntry entry : entries) {
            if (!entry.isBeingTested() && !entry.wasTestedWithin(FRESH_FOR_MS)) {
                testAsync(entry);
            }
        }
    }

    private void test(ProxyEntry entry) {
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

    private static String shorten(String message) {
        if (message == null || message.isEmpty()) {
            return "failed";
        }
        return message.length() > 28 ? message.substring(0, 28) : message;
    }
}
