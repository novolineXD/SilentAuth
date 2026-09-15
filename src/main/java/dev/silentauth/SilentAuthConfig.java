package dev.silentauth;

import dev.silentauth.auth.MicrosoftAuth;
import dev.silentauth.proxy.ProxyType;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class SilentAuthConfig {

    private static final String GENERAL = "general";
    private static final String PROXY = "proxy";

    private final Configuration configuration;

    private boolean proxyAuthRequests = true;
    private boolean refreshOnLogin = true;
    private boolean showMainMenuButton = true;
    private boolean restoreLastAccount;
    private ProxyType defaultProxyType = ProxyType.SOCKS5;

    public SilentAuthConfig(File file) {
        this.configuration = new Configuration(file);
        load();
    }

    public void load() {
        configuration.load();
        proxyAuthRequests = configuration.getBoolean("proxyAuthRequests", PROXY, true,
                "Send login and session requests through the proxy bound to the account");
        defaultProxyType = ProxyType.byName(configuration.getString("defaultType", PROXY, "SOCKS5",
                "Type assumed for proxies pasted without a scheme"), ProxyType.SOCKS5);
        refreshOnLogin = configuration.getBoolean("refreshOnLogin", GENERAL, true,
                "Refresh expired Microsoft tokens automatically when switching to that account");
        showMainMenuButton = configuration.getBoolean("showMainMenuButton", GENERAL, true,
                "Add the SilentAuth button to the main menu and the multiplayer screen");
        restoreLastAccount = configuration.getBoolean("restoreLastAccount", GENERAL, false,
                "Switch back to the last used account when the game starts");
        MicrosoftAuth.setClientId(configuration.getString("microsoftClientId", GENERAL,
                MicrosoftAuth.DEFAULT_CLIENT_ID, "Azure application id used for the device code sign in"));
        save();
    }

    public void save() {
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public boolean isProxyAuthRequests() {
        return proxyAuthRequests;
    }

    public boolean isRefreshOnLogin() {
        return refreshOnLogin;
    }

    public boolean isShowMainMenuButton() {
        return showMainMenuButton;
    }

    public boolean isRestoreLastAccount() {
        return restoreLastAccount;
    }

    public ProxyType getDefaultProxyType() {
        return defaultProxyType;
    }
}
