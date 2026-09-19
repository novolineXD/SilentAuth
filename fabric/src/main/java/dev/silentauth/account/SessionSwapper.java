package dev.silentauth.account;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.silentauth.fabric.mixin.MinecraftClientAccessor;
import dev.silentauth.net.ProxyAuthenticator;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.util.ApiServices;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Swaps the session {@link MinecraftClient} is holding, and rebuilds the api services on top of
 * the proxy that session should use. The fields are reached through {@link MinecraftClientAccessor}
 * rather than reflection, which is the mixin-based equivalent of what the 1.8.9 build does.
 *
 * <p>Must be called on the client thread.</p>
 */
public final class SessionSwapper {

    /** Offline servers ignore the token, but an empty one produces a malformed session id. */
    private static final String OFFLINE_TOKEN = "0";

    /** The session the launcher started the game with, so it can always be put back. */
    private static Session original;

    private SessionSwapper() {
    }

    private static MinecraftClientAccessor accessor() {
        return (MinecraftClientAccessor) MinecraftClient.getInstance();
    }

    /** Remembers the launcher's own session. Called once at startup, before anything swaps it. */
    public static void captureOriginal() {
        if (original == null) {
            original = MinecraftClient.getInstance().getSession();
        }
    }

    public static boolean hasOriginal() {
        return original != null;
    }

    public static String originalUsername() {
        return original == null ? "unknown" : original.getUsername();
    }

    /**
     * Spoofs just the name the client and offline servers see, leaving the proxy and services
     * alone. Only works on cracked/offline-mode servers - a premium server checks the name against
     * Mojang and will reject a spoofed one.
     */
    public static void spoof(String username) {
        String name = username.trim();
        Session session = new Session(name, uuidOf(Account.offlineUuid(name)), OFFLINE_TOKEN,
                Optional.empty(), Optional.empty());
        accessor().silentauth$setSession(session);
        Log.info("Spoofed the session name to " + name);
    }

    /** Puts the launcher's session back and stops using any proxy. */
    public static void restoreOriginal() {
        if (original == null) {
            return;
        }
        accessor().silentauth$setSession(original);
        applyProxy(null);
        Log.info("Restored the original session (" + original.getUsername() + ")");
    }

    public static void apply(Account account, ProxyEntry proxy) {
        boolean offline = account.getType() == AccountType.OFFLINE;
        Session session = new Session(account.getUsername(), uuidOf(account.getUuid()),
                offline ? OFFLINE_TOKEN : account.getAccessToken(),
                Optional.empty(), Optional.empty());

        accessor().silentauth$setSession(session);
        applyProxy(proxy);

        account.markUsed();
        Log.info("Switched session to " + account.getUsername() + " [" + account.getType().getLabel() + ", token "
                + Log.redact(account.getAccessToken()) + ", proxy " + (proxy == null ? "none" : proxy.describe()) + "]");
    }

    /**
     * Points the game's api services at the given proxy. The services are rebuilt rather than
     * mutated because authlib takes its proxy in the constructor, and the credentials are handed to
     * {@link ProxyAuthenticator} because those calls run on Minecraft's threads.
     */
    public static void applyProxy(ProxyEntry proxy) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Proxy javaProxy = proxy == null ? Proxy.NO_PROXY : proxy.toJavaProxy();
        ProxyAuthenticator.setSessionProxy(proxy);

        YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(javaProxy);
        ApiServices services = ApiServices.create(auth, mc.runDirectory);
        accessor().silentauth$setNetworkProxy(javaProxy);
        accessor().silentauth$setApiServices(services);
    }

    public static String currentUsername() {
        Session session = MinecraftClient.getInstance().getSession();
        return session == null ? "unknown" : session.getUsername();
    }

    /** Accepts a dashed or undashed uuid, falling back to a name-based one for junk input. */
    private static UUID uuidOf(String raw) {
        if (raw == null || raw.isEmpty()) {
            return UUID.randomUUID();
        }
        String s = raw.trim();
        try {
            if (s.contains("-")) {
                return UUID.fromString(s);
            }
            if (s.length() == 32) {
                return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                        + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20));
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to a name-derived uuid
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + s).getBytes(StandardCharsets.UTF_8));
    }
}
