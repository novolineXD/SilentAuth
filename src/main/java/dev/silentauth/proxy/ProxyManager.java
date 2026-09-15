package dev.silentauth.proxy;

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

public final class ProxyManager {

    private final JsonStore store;
    private final Crypto crypto;
    private final List<ProxyEntry> proxies = new CopyOnWriteArrayList<ProxyEntry>();
    private volatile String defaultProxyId = "";

    public ProxyManager(File directory, Crypto crypto) {
        this.store = new JsonStore(new File(directory, "proxies.json"), "proxies.json");
        this.crypto = crypto;
    }

    public List<ProxyEntry> all() {
        return new ArrayList<ProxyEntry>(proxies);
    }

    public int size() {
        return proxies.size();
    }

    public ProxyEntry byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (ProxyEntry entry : proxies) {
            if (entry.getId().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public ProxyEntry byEndpoint(String host, int port) {
        if (host == null) {
            return null;
        }
        String wanted = host.trim();
        for (ProxyEntry entry : proxies) {
            if (entry.getPort() == port && entry.getHost().equalsIgnoreCase(wanted)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Stores the proxy, or updates the stored one that already has the same host and port so
     * the same endpoint is never listed twice.
     *
     * @return the entry that is now stored, which may be the pre-existing one
     */
    public ProxyEntry add(ProxyEntry entry) {
        if (entry == null) {
            return null;
        }
        ProxyEntry existing = byEndpoint(entry.getHost(), entry.getPort());
        if (existing == null) {
            proxies.add(entry);
            save();
            return entry;
        }
        existing.setType(entry.getType());
        existing.setUsername(entry.getUsername());
        existing.setPassword(entry.getPassword());
        if (!entry.getLabel().isEmpty()) {
            existing.setLabel(entry.getLabel());
        }
        save();
        return existing;
    }

    public void remove(ProxyEntry entry) {
        if (entry == null) {
            return;
        }
        proxies.remove(entry);
        if (entry.getId().equals(defaultProxyId)) {
            defaultProxyId = "";
        }
        save();
    }

    public ProxyEntry getDefault() {
        return byId(defaultProxyId);
    }

    public void setDefault(ProxyEntry entry) {
        defaultProxyId = entry == null ? "" : entry.getId();
        save();
    }

    public boolean isDefault(ProxyEntry entry) {
        return entry != null && entry.getId().equals(defaultProxyId);
    }

    public void load() {
        proxies.clear();
        JsonObject root = store.read();
        defaultProxyId = Json.string(root, "default", "");
        JsonArray array = Json.array(root, "proxies");
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            ProxyEntry entry = new ProxyEntry(
                    Json.string(object, "id", ""),
                    ProxyType.byName(Json.string(object, "type", "SOCKS5"), ProxyType.SOCKS5),
                    Json.string(object, "host", ""),
                    (int) Json.number(object, "port", 0),
                    Json.string(object, "username", ""),
                    crypto.decrypt(Json.string(object, "password", "")),
                    Json.string(object, "label", ""));
            if (!entry.getHost().isEmpty() && entry.getPort() > 0) {
                proxies.add(entry);
            }
        }
        Log.info("Loaded " + proxies.size() + " proxies");
    }

    public void save() {
        JsonArray array = new JsonArray();
        for (ProxyEntry entry : proxies) {
            JsonObject object = new JsonObject();
            object.addProperty("id", entry.getId());
            object.addProperty("type", entry.getType().name());
            object.addProperty("host", entry.getHost());
            object.addProperty("port", entry.getPort());
            object.addProperty("username", entry.getUsername());
            object.addProperty("password", crypto.encrypt(entry.getPassword()));
            object.addProperty("label", entry.getLabel());
            array.add(object);
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("default", defaultProxyId);
        root.add("proxies", array);
        store.write(root);
    }
}
