package dev.silentauth.proxy;

/**
 * Parses a pasted proxy in any of the shapes providers hand out, including the residential
 * ones:
 *
 * <ul>
 *   <li>{@code host:port}</li>
 *   <li>{@code host:port:user:pass} (the form most residential providers give)</li>
 *   <li>{@code user:pass@host:port}</li>
 *   <li>{@code scheme://user:pass@host:port} (a full connection string)</li>
 * </ul>
 *
 * <p>The password may itself contain colons, the username may contain the dots, dashes and
 * underscores residential logins use, and a trailing slash is ignored, so a string copied
 * straight from a provider dashboard parses as-is.</p>
 */
public final class ProxyParser {

    private ProxyParser() {
    }

    public static ProxyEntry parse(String raw, ProxyType fallbackType) {
        if (raw == null) {
            throw new IllegalArgumentException("Empty proxy");
        }
        String line = raw.trim();
        while (line.endsWith("/")) {
            line = line.substring(0, line.length() - 1).trim();
        }
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
        } else if (parts.length >= 4 && credentials.isEmpty()) {
            // host:port:user:pass, with the password allowed to carry more colons.
            host = parts[0];
            port = parsePort(parts[1]);
            credentials = parts[2] + ":" + join(parts, 3);
        } else if (parts.length == 3 && credentials.isEmpty()) {
            // host:port:user, a username with no password.
            host = parts[0];
            port = parsePort(parts[1]);
            credentials = parts[2];
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

    private static String join(String[] parts, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
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
