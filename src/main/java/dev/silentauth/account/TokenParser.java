package dev.silentauth.account;

public final class TokenParser {

    private TokenParser() {
    }

    public static Parsed parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Nothing was pasted");
        }
        String value = raw.trim();
        if (value.toLowerCase().startsWith("token:")) {
            value = value.substring(6);
        }

        String[] parts = value.split("[:;,\\s]+");
        String token = "";
        String uuid = "";
        String username = "";

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (looksLikeUuid(part)) {
                uuid = part.replace("-", "");
            } else if (part.length() > token.length() && part.length() >= 16) {
                if (!token.isEmpty() && username.isEmpty() && isName(token)) {
                    username = token;
                }
                token = part;
            } else if (isName(part) && username.isEmpty()) {
                username = part;
            }
        }

        if (token.isEmpty()) {
            throw new IllegalArgumentException("No session token in that text");
        }
        return new Parsed(token, username, uuid);
    }

    private static boolean looksLikeUuid(String part) {
        String stripped = part.replace("-", "");
        if (stripped.length() != 32) {
            return false;
        }
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean isName(String part) {
        if (part.isEmpty() || part.length() > 16) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    public static final class Parsed {

        public final String token;
        public final String username;
        public final String uuid;

        Parsed(String token, String username, String uuid) {
            this.token = token;
            this.username = username;
            this.uuid = uuid;
        }
    }
}
