package dev.silentauth;

import dev.silentauth.account.AccountManager;
import dev.silentauth.command.CommandSilentAuth;
import dev.silentauth.event.MainMenuHandler;
import dev.silentauth.proxy.ProxyManager;
import dev.silentauth.proxy.ProxyTester;
import dev.silentauth.util.Crypto;
import dev.silentauth.util.Log;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
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

    private File directory;
    private SilentAuthConfig config;
    private AccountManager accounts;
    private ProxyManager proxies;
    private ProxyTester tester;

    public static SilentAuth get() {
        return instance;
    }

    public static AccountManager accounts() {
        return instance.accounts;
    }

    public static ProxyManager proxies() {
        return instance.proxies;
    }

    public static ProxyTester tester() {
        return instance.tester;
    }

    public static SilentAuthConfig config() {
        return instance.config;
    }

    public File getDirectory() {
        return directory;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        directory = new File(event.getModConfigurationDirectory(), MOD_ID);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Log.warn("Could not create " + directory);
        }
        config = new SilentAuthConfig(new File(directory, "silentauth.cfg"));

        Crypto crypto = Crypto.forDirectory(directory);
        proxies = new ProxyManager(directory, crypto);
        accounts = new AccountManager(directory, crypto);
        proxies.load();
        accounts.load();
        tester = new ProxyTester();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new MainMenuHandler());
        ClientCommandHandler.instance.registerCommand(new CommandSilentAuth());
        Log.info(MOD_NAME + " ready with " + accounts.size() + " accounts and " + proxies.size() + " proxies");
    }
}
