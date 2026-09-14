package dev.silentauth.account;

import dev.silentauth.SilentAuth;
import dev.silentauth.auth.AuthException;
import dev.silentauth.auth.SessionTokenAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class AccountImporter {

    public interface Progress {
        void onProgress(int done, int total, String line);
    }

    private AccountImporter() {
    }

    public static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
        } finally {
            reader.close();
        }
        return lines;
    }

    public static Result importAll(List<String> lines, ProxyEntry proxy, Progress progress) {
        int added = 0;
        int failed = 0;
        List<String> problems = new ArrayList<String>();

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (progress != null) {
                progress.onProgress(index, lines.size(), line);
            }
            try {
                Parsed parsed = parse(line);
                Account account;
                if (!parsed.username.isEmpty() && !parsed.uuid.isEmpty()) {
                    account = SessionTokenAuth.withoutLookup(parsed.token, parsed.username, parsed.uuid);
                } else {
                    account = SessionTokenAuth.login(parsed.token, proxy);
                }
                if (proxy != null) {
                    account.setProxyId(proxy.getId());
                }
                SilentAuth.accounts().add(account);
                added++;
            } catch (AuthException e) {
                failed++;
                problems.add(shorten(line) + ": " + e.getMessage());
            } catch (IllegalArgumentException e) {
                failed++;
                problems.add(shorten(line) + ": " + e.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            for (String problem : problems) {
                Log.warn("Import skipped " + problem);
            }
        }
        return new Result(added, failed, problems);
    }

    public static Parsed parse(String line) {
        String value = line.trim();
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
            throw new IllegalArgumentException("No token on that line");
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

    private static String shorten(String line) {
        return line.length() > 24 ? line.substring(0, 24) + "..." : line;
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

    public static final class Result {

        public final int added;
        public final int failed;
        public final List<String> problems;

        Result(int added, int failed, List<String> problems) {
            this.added = added;
            this.failed = failed;
            this.problems = problems;
        }
    }
}
