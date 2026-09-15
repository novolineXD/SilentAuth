package dev.silentauth.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.silentauth.util.Crypto;
import dev.silentauth.util.Json;
import dev.silentauth.util.JsonStore;
import dev.silentauth.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AccountManager {

    private final JsonStore store;
    private final Crypto crypto;
    private final List<Account> accounts = new CopyOnWriteArrayList<Account>();
    private volatile String activeId = "";

    public AccountManager(File directory, Crypto crypto) {
        this.store = new JsonStore(new File(directory, "accounts.json"), "accounts.json");
        this.crypto = crypto;
    }

    public List<Account> all() {
        return new ArrayList<Account>(accounts);
    }

    public List<Account> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.isEmpty()) {
            return all();
        }
        List<Account> matches = new ArrayList<Account>();
        for (Account account : accounts) {
            if (account.getUsername().toLowerCase().contains(needle)
                    || account.getType().getLabel().toLowerCase().contains(needle)
                    || account.getStrippedUuid().toLowerCase().contains(needle)) {
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
        String wanted = username.trim();
        for (Account account : accounts) {
            if (account.getUsername().equalsIgnoreCase(wanted)) {
                return account;
            }
        }
        return null;
    }

    /** Adds the account, or folds it into the stored one that has the same uuid. */
    public void add(Account account) {
        if (account == null) {
            return;
        }
        Account existing = byUuid(account.getStrippedUuid());
        if (existing == null) {
            accounts.add(account);
        } else {
            existing.setUsername(account.getUsername());
            existing.setAccessToken(account.getAccessToken());
            existing.setRefreshToken(account.getRefreshToken());
            existing.setTokenExpiresAt(account.getTokenExpiresAt());
            existing.setType(account.getType());
            existing.setValidity(account.getValidity(), account.getDetail());
            if (!account.getProxyId().isEmpty()) {
                existing.setProxyId(account.getProxyId());
            }
        }
        save();
    }

    private Account byUuid(String strippedUuid) {
        if (strippedUuid.isEmpty()) {
            return null;
        }
        for (Account candidate : accounts) {
            if (candidate.getStrippedUuid().equalsIgnoreCase(strippedUuid)) {
                return candidate;
            }
        }
        return null;
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
        JsonObject root = store.read();
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
    }

    public void save() {
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

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("active", activeId);
        root.add("accounts", array);
        store.write(root);
    }
}
