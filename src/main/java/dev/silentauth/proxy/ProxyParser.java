package dev.silentauth.proxy;

public final class ProxyParser {

    private ProxyParser() {
    }

    public static ProxyEntry parse(String raw, ProxyType fallbackType) {
        if (raw == null) {
            throw new IllegalArgumentException("Empty proxy");
        }
        String line = raw.trim();
        if (line.isEmpty()) {
            throw new IllegalArgumentException("Empty proxy");
        }

        ProxyType type = fallbackType == null ? ProxyType.SOCKS5 : fallbackType;
        int scheme = line.indexOf("://");
        if (scheme > 0) {
            type = ProxyType.byName(line.substring(0, scheme), type);
            line = line.substring(scheme + 3);
        }

        String credentials = "";
        int at = line.lastIndexOf('@');
        if (at >= 0) {
            credentials = line.substring(0, at);
            line = line.substring(at + 1);
        }

        String[] parts = line.split(":");
        String host;
        int port;
        if (parts.length == 2) {
            host = parts[0];
            port = parsePort(parts[1]);
        } else if (parts.length == 4 && credentials.isEmpty()) {
            host = parts[0];
            port = parsePort(parts[1]);
            credentials = parts[2] + ":" + parts[3];
        } else {
            throw new IllegalArgumentException("Expected host:port, host:port:user:pass or user:pass@host:port");
        }

        if (host.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing proxy host");
        }

        String username = "";
        String password = "";
        if (!credentials.isEmpty()) {
            int split = credentials.indexOf(':');
            if (split < 0) {
                username = credentials;
            } else {
                username = credentials.substring(0, split);
                password = credentials.substring(split + 1);
            }
        }

        return new ProxyEntry(type, host.trim(), port, username, password);
    }

    private static int parsePort(String raw) {
        int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port '" + raw + "' is not a number");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port " + port + " is out of range");
        }
        return port;
    }
}
