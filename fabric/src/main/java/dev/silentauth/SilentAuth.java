package dev.silentauth;

import dev.silentauth.account.Account;
import dev.silentauth.account.AccountChecker;
import dev.silentauth.account.AccountManager;
import dev.silentauth.account.LoginService;
import dev.silentauth.net.ProxyAuthenticator;
import dev.silentauth.proxy.ProxyManager;
import dev.silentauth.proxy.ProxyTester;
import dev.silentauth.util.Crypto;
import dev.silentauth.util.Log;

import java.io.File;

/** Holds the managers and config, shared by the Fabric client entry and the screens. */
public final class SilentAuth {

    public static final String MOD_ID = "silentauth";
    public static final String VERSION = "1.2.0";

    private static AccountManager accounts;
    private static ProxyManager proxies;
    private static ProxyTester tester;
    private static AccountChecker checker;
    private static SilentAuthConfig config;

    private SilentAuth() {
    }

    /** Sets everything up under the given directory. Called once from the client entry point. */
    public static void init(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            System.out.println("[SilentAuth] could not create " + directory);
        }
        config = new SilentAuthConfig(new File(directory, "silentauth.json"));
        ProxyAuthenticator.install();

        Crypto crypto = Crypto.forDirectory(directory);
        proxies = new ProxyManager(directory, crypto);
        accounts = new AccountManager(directory, crypto);
        proxies.load();
        accounts.load();
        tester = new ProxyTester();
        checker = new AccountChecker();
    }

    public static AccountManager accounts() {
        return accounts;
    }

    public static ProxyManager proxies() {
        return proxies;
    }

    public static ProxyTester tester() {
        return tester;
    }

    public static AccountChecker checker() {
        return checker;
    }

    public static SilentAuthConfig config() {
        return config;
    }

    /**
     * Puts the last used account back after a restart, if that is turned on. Called once the title
     * screen is up rather than during startup, so the game is fully built first.
     */
    public static void restoreLastAccount() {
        if (config == null || !config.isRestoreLastAccount()) {
            return;
        }
        Account active = accounts.getActive();
        if (active == null) {
            return;
        }
        Log.info("Restoring the last used account: " + active.getUsername());
        LoginService.loginAsync(active, null);
    }
}
