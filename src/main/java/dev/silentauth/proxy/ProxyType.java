package dev.silentauth.proxy;

public enum ProxyType {

    HTTP("HTTP"),
    SOCKS4("SOCKS4"),
    SOCKS5("SOCKS5");

    private final String label;

    ProxyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public ProxyType next() {
        ProxyType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ProxyType byName(String name, ProxyType fallback) {
        if (name == null) {
            return fallback;
        }
        String cleaned = name.trim().toLowerCase();
        if (cleaned.equals("http") || cleaned.equals("https")) {
            return HTTP;
        }
        if (cleaned.equals("socks4") || cleaned.equals("socks4a")) {
            return SOCKS4;
        }
        if (cleaned.equals("socks") || cleaned.equals("socks5") || cleaned.equals("socks5h")) {
            return SOCKS5;
        }
        for (ProxyType type : values()) {
            if (type.name().equalsIgnoreCase(cleaned)) {
                return type;
            }
        }
        return fallback;
    }
}
