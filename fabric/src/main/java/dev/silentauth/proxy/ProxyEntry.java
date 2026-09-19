package dev.silentauth.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.UUID;

public final class ProxyEntry {

    public static final int UNTESTED = -1;
    public static final int TESTING = -3;
    public static final int UNREACHABLE = -2;

    private final String id;
    private ProxyType type;
    private final String host;
    private final int port;
    private String username;
    private String password;
    private String label;
    private volatile int latencyMs = UNTESTED;
    private volatile String lastError = "";
    private volatile long testedAt;
    private volatile Proxy cached;

    public ProxyEntry(ProxyType type, String host, int port, String username, String password) {
        this(UUID.randomUUID().toString(), type, host, port, username, password, "");
    }

    public ProxyEntry(String id, ProxyType type, String host, int port, String username, String password, String label) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.type = type == null ? ProxyType.SOCKS5 : type;
        this.host = host;
        this.port = port;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.label = label == null ? "" : label;
    }

    public String getId() {
        return id;
    }

    public ProxyType getType() {
        return type;
    }

    public void setType(ProxyType type) {
        this.type = type;
        this.cached = null;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
        if (latencyMs >= 0 || latencyMs == UNREACHABLE) {
            this.testedAt = System.currentTimeMillis();
        }
    }

    public boolean isReachable() {
        return latencyMs >= 0;
    }

    public boolean isBeingTested() {
        return latencyMs == TESTING;
    }

    /** True when it was tested recently enough not to be worth testing again. */
    public boolean wasTestedWithin(long millis) {
        return testedAt > 0L && System.currentTimeMillis() - testedAt < millis;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? "" : lastError;
    }

    public boolean hasCredentials() {
        return !username.isEmpty();
    }

    /**
     * The JDK proxy for this entry, resolved once and kept. Building it hits DNS, and this is
     * read from the render thread every time the account list draws a row.
     */
    public Proxy toJavaProxy() {
        Proxy local = cached;
        if (local == null) {
            Proxy.Type javaType = type == ProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
            local = new Proxy(javaType, new InetSocketAddress(host, port));
            cached = local;
        }
        return local;
    }

    public String describe() {
        return type.getLabel().toLowerCase() + "://" + host + ":" + port;
    }

    public String displayName() {
        if (!label.isEmpty()) {
            return label + " (" + host + ":" + port + ")";
        }
        return host + ":" + port;
    }

    public String statusText() {
        if (latencyMs == TESTING) {
            return "checking";
        }
        if (latencyMs == UNTESTED) {
            return "";
        }
        if (latencyMs == UNREACHABLE) {
            return lastError.isEmpty() ? "unreachable" : lastError;
        }
        return latencyMs + " ms";
    }

    @Override
    public String toString() {
        return describe();
    }
}
