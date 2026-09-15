package dev.silentauth;

import dev.silentauth.account.Account;
import dev.silentauth.account.AccountChecker;
import dev.silentauth.account.AccountManager;
import dev.silentauth.account.LoginService;
import dev.silentauth.account.SessionSwapper;
import dev.silentauth.command.CommandSilentAuth;
import dev.silentauth.event.MainMenuHandler;
import dev.silentauth.net.ProxyAuthenticator;
import dev.silentauth.proxy.ProxyManager;
import dev.silentauth.proxy.ProxyTester;
import dev.silentauth.util.Crypto;
import dev.silentauth.util.Log;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

@Mod(modid = SilentAuth.MOD_ID,
        name = SilentAuth.MOD_NAME,
        version = SilentAuth.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public final class SilentAuth {

    public static final String MOD_ID = "silentauth";
    public static final String MOD_NAME = "SilentAuth";
    public static final String VERSION = "@MOD_VERSION@";

    @Mod.Instance(MOD_ID)
    private static SilentAuth instance;

    private SilentAuthConfig config;
    private AccountManager accounts;
    private ProxyManager proxies;
    private ProxyTester tester;
    private AccountChecker checker;

    public static AccountManager accounts() {
        return instance.accounts;
    }

    public static ProxyManager proxies() {
        return instance.proxies;
    }

    public static ProxyTester tester() {
        return instance.tester;
    }

    public static AccountChecker checker() {
        return instance.checker;
    }

    public static SilentAuthConfig config() {
        return instance.config;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File directory = new File(event.getModConfigurationDirectory(), MOD_ID);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Log.warn("Could not create " + directory + ", nothing will be saved");
        }
        config = new SilentAuthConfig(new File(directory, "silentauth.cfg"));

        // Before anything makes a request, so proxies that need credentials work from the start.
        ProxyAuthenticator.install();

        Crypto crypto = Crypto.forDirectory(directory);
        proxies = new ProxyManager(directory, crypto);
        accounts = new AccountManager(directory, crypto);
        proxies.load();
        accounts.load();
        tester = new ProxyTester();
        checker = new AccountChecker();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        SessionSwapper.captureOriginal();
        MainMenuHandler handler = new MainMenuHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance().bus().register(handler);
        ClientCommandHandler.instance.registerCommand(new CommandSilentAuth());
        Log.info(MOD_NAME + " ready with " + accounts.size() + " accounts and " + proxies.size() + " proxies");
    }

    /**
     * Puts the last used account back after a restart, if that is turned on. Called once the
     * title screen is up rather than during startup, so the game is fully built first.
     */
    public static void restoreLastAccount() {
        if (!config().isRestoreLastAccount()) {
            return;
        }
        Account active = accounts().getActive();
        if (active == null) {
            return;
        }
        Log.info("Restoring the last used account: " + active.getUsername());
        LoginService.loginAsync(active, null);
    }
}
