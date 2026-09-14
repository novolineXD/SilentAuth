package dev.silentauth.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.UUID;

public final class ProxyEntry {

    public static final int UNTESTED = -1;
    public static final int UNREACHABLE = -2;

    private final String id;
    private ProxyType type;
    private String host;
    private int port;
    private String username;
    private String password;
    private String label;
    private volatile int latencyMs = UNTESTED;
    private volatile String lastError = "";

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
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? "" : lastError;
    }

    public boolean hasCredentials() {
        return !username.isEmpty();
    }

    public InetSocketAddress getAddress() {
        return InetSocketAddress.createUnresolved(host, port);
    }

    public Proxy toJavaProxy() {
        Proxy.Type javaType = type == ProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        return new Proxy(javaType, new InetSocketAddress(host, port));
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
        if (latencyMs == UNTESTED) {
            return "untested";
        }
        if (latencyMs == UNREACHABLE) {
            return lastError.isEmpty() ? "unreachable" : lastError;
        }
        return latencyMs + " ms";
    }

    public String toStorageString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getLabel().toLowerCase()).append("://");
        if (hasCredentials()) {
            sb.append(username).append(':').append(password).append('@');
        }
        sb.append(host).append(':').append(port);
        return sb.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
