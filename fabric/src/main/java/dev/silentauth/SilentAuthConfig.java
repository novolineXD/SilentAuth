package dev.silentauth;

import com.google.gson.JsonObject;
import dev.silentauth.auth.MicrosoftAuth;
import dev.silentauth.proxy.ProxyType;
import dev.silentauth.util.Json;
import dev.silentauth.util.JsonStore;

import java.io.File;

/** Simple JSON config, the Fabric counterpart of the Forge Configuration-backed one. */
public final class SilentAuthConfig {

    private final JsonStore store;

    private boolean proxyAuthRequests = true;
    private boolean routeGameThroughProxy = true;
    private boolean refreshOnLogin = true;
    private boolean restoreLastAccount;
    private ProxyType defaultProxyType = ProxyType.SOCKS5;
    private String microsoftClientId = MicrosoftAuth.DEFAULT_CLIENT_ID;

    public SilentAuthConfig(File file) {
        this.store = new JsonStore(file, "silentauth.json");
        load();
    }

    public void load() {
        JsonObject o = store.read();
        proxyAuthRequests = bool(o, "proxyAuthRequests", true);
        routeGameThroughProxy = bool(o, "routeGameThroughProxy", true);
        refreshOnLogin = bool(o, "refreshOnLogin", true);
        restoreLastAccount = bool(o, "restoreLastAccount", false);
        defaultProxyType = ProxyType.byName(Json.string(o, "defaultProxyType", "SOCKS5"), ProxyType.SOCKS5);
        microsoftClientId = Json.string(o, "microsoftClientId", MicrosoftAuth.DEFAULT_CLIENT_ID);
        MicrosoftAuth.setClientId(microsoftClientId);
        save();
    }

    private void save() {
        JsonObject o = new JsonObject();
        o.addProperty("proxyAuthRequests", proxyAuthRequests);
        o.addProperty("routeGameThroughProxy", routeGameThroughProxy);
        o.addProperty("refreshOnLogin", refreshOnLogin);
        o.addProperty("restoreLastAccount", restoreLastAccount);
        o.addProperty("defaultProxyType", defaultProxyType.name());
        o.addProperty("microsoftClientId", microsoftClientId);
        store.write(o);
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean isProxyAuthRequests() {
        return proxyAuthRequests;
    }

    public boolean isRouteGameThroughProxy() {
        return routeGameThroughProxy;
    }

    public boolean isRefreshOnLogin() {
        return refreshOnLogin;
    }

    public boolean isRestoreLastAccount() {
        return restoreLastAccount;
    }

    public ProxyType getDefaultProxyType() {
        return defaultProxyType;
    }
}
