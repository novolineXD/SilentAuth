package dev.silentauth.proxy;

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

public final class ProxyManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Crypto crypto;
    private final List<ProxyEntry> proxies = new CopyOnWriteArrayList<ProxyEntry>();
    private String defaultProxyId = "";

    public ProxyManager(File directory, Crypto crypto) {
        this.file = new File(directory, "proxies.json");
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

    public void add(ProxyEntry entry) {
        if (entry == null) {
            return;
        }
        proxies.add(entry);
        save();
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
                        ProxyType.byName(Json.string(object, "type", "socks5"), ProxyType.SOCKS5),
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
        } catch (IOException e) {
            Log.error("Could not read proxies.json", e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("default", defaultProxyId);
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
        root.add("proxies", array);

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
        } catch (IOException e) {
            Log.error("Could not write proxies.json", e);
        }
    }
}
