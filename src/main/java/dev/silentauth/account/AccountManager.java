package dev.silentauth.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.silentauth.util.Crypto;
import dev.silentauth.util.Json;
import dev.silentauth.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AccountManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Crypto crypto;
    private final List<Account> accounts = new CopyOnWriteArrayList<Account>();
    private String activeId = "";

    public AccountManager(File directory, Crypto crypto) {
        this.file = new File(directory, "accounts.json");
        this.crypto = crypto;
    }

    public List<Account> all() {
        return new ArrayList<Account>(accounts);
    }

    public List<Account> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return all();
        }
        String needle = query.trim().toLowerCase();
        List<Account> matches = new ArrayList<Account>();
        for (Account account : accounts) {
            if (account.getUsername().toLowerCase().contains(needle)
                    || account.getType().getLabel().toLowerCase().contains(needle)) {
                matches.add(account);
            }
        }
        return matches;
    }

    public int size() {
        return accounts.size();
    }

    public Account byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (Account account : accounts) {
            if (account.getId().equals(id)) {
                return account;
            }
        }
        return null;
    }

    public Account byUsername(String username) {
        if (username == null) {
            return null;
        }
        for (Account account : accounts) {
            if (account.getUsername().equalsIgnoreCase(username.trim())) {
                return account;
            }
        }
        return null;
    }

    public void add(Account account) {
        if (account == null) {
            return;
        }
        Account existing = null;
        if (!account.getUuid().isEmpty()) {
            for (Account candidate : accounts) {
                if (!candidate.getUuid().isEmpty()
                        && candidate.getStrippedUuid().equalsIgnoreCase(account.getStrippedUuid())) {
                    existing = candidate;
                    break;
                }
            }
        }
        if (existing != null) {
            existing.setUsername(account.getUsername());
            existing.setAccessToken(account.getAccessToken());
            existing.setRefreshToken(account.getRefreshToken());
            existing.setTokenExpiresAt(account.getTokenExpiresAt());
            existing.setType(account.getType());
        } else {
            accounts.add(account);
        }
        save();
    }

    public void remove(Account account) {
        if (account == null) {
            return;
        }
        accounts.remove(account);
        if (account.getId().equals(activeId)) {
            activeId = "";
        }
        save();
    }

    public Account getActive() {
        return byId(activeId);
    }

    public void setActive(Account account) {
        activeId = account == null ? "" : account.getId();
        if (account != null) {
            account.markUsed();
        }
        save();
    }

    public boolean isActive(Account account) {
        return account != null && account.getId().equals(activeId);
    }

    public void load() {
        accounts.clear();
        if (!file.isFile()) {
            return;
        }
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            try {
                while ((read = reader.read(buffer)) > 0) {
                    sb.append(buffer, 0, read);
                }
            } finally {
                reader.close();
            }
            JsonObject root = Json.parseObject(sb.toString());
            activeId = Json.string(root, "active", "");
            JsonArray array = Json.array(root, "accounts");
            if (array == null) {
                return;
            }
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                Account account = new Account(
                        Json.string(object, "id", ""),
                        AccountType.byName(Json.string(object, "type", "SESSION"), AccountType.SESSION),
                        Json.string(object, "username", ""),
                        Json.string(object, "uuid", ""),
                        crypto.decrypt(Json.string(object, "accessToken", "")),
                        crypto.decrypt(Json.string(object, "refreshToken", "")),
                        Json.number(object, "tokenExpiresAt", 0L),
                        Json.string(object, "proxyId", ""),
                        Json.number(object, "addedAt", 0L),
                        Json.number(object, "lastUsedAt", 0L));
                if (!account.getUsername().isEmpty()) {
                    accounts.add(account);
                }
            }
            Log.info("Loaded " + accounts.size() + " accounts");
        } catch (IOException e) {
            Log.error("Could not read accounts.json", e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("active", activeId);
        JsonArray array = new JsonArray();
        for (Account account : accounts) {
            JsonObject object = new JsonObject();
            object.addProperty("id", account.getId());
            object.addProperty("type", account.getType().name());
            object.addProperty("username", account.getUsername());
            object.addProperty("uuid", account.getUuid());
            object.addProperty("accessToken", crypto.encrypt(account.getAccessToken()));
            object.addProperty("refreshToken", crypto.encrypt(account.getRefreshToken()));
            object.addProperty("tokenExpiresAt", account.getTokenExpiresAt());
            object.addProperty("proxyId", account.getProxyId());
            object.addProperty("addedAt", account.getAddedAt());
            object.addProperty("lastUsedAt", account.getLastUsedAt());
            array.add(object);
        }
        root.add("accounts", array);

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            try {
                GSON.toJson(root, writer);
            } finally {
                writer.close();
            }
            file.setReadable(false, false);
            file.setReadable(true, true);
        } catch (IOException e) {
            Log.error("Could not write accounts.json", e);
        }
    }
}
