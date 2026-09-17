package dev.silentauth.account;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.silentauth.net.ProxyAuthenticator;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;
import dev.silentauth.util.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.net.Proxy;
import java.util.UUID;

/**
 * Swaps the session Minecraft is holding, and rebuilds the session service on top of the
 * proxy that session should use.
 *
 * <p>All three fields are private and final on Minecraft, and each is the only one of its type
 * there, so {@link Reflect} looks them up by the mapped and obfuscated names and falls back to
 * the field type. Must be called on the client thread.</p>
 */
public final class SessionSwapper {

    private static final String[] SESSION_FIELD = { "session", "field_71449_j" };
    private static final String[] SESSION_SERVICE_FIELD = { "sessionService", "field_152355_az" };
    private static final String[] PROXY_FIELD = { "proxy", "field_110453_aa" };

    /** Offline servers ignore the token, but an empty one produces a malformed session id. */
    private static final String OFFLINE_TOKEN = "0";

    /** The session the launcher started the game with, so it can always be put back. */
    private static Session original;

    private SessionSwapper() {
    }

    /** Remembers the launcher's own session. Called once at startup, before anything swaps it. */
    public static void captureOriginal() {
        if (original == null) {
            original = Minecraft.getMinecraft().getSession();
        }
    }

    public static boolean hasOriginal() {
        return original != null;
    }

    public static String originalUsername() {
        return original == null ? "unknown" : original.getUsername();
    }

    /**
     * Spoofs just the name the client and offline servers see, leaving the proxy and session
     * service alone. Only works on cracked/offline-mode servers - a premium server checks the
     * name against Mojang and will reject a spoofed one.
     */
    public static void spoof(String username) {
        String name = username.trim();
        Session session = new Session(name, Account.offlineUuid(name), OFFLINE_TOKEN, "legacy");
        Reflect.set(Minecraft.getMinecraft(), Minecraft.class, Session.class, session, SESSION_FIELD);
        Log.info("Spoofed the session name to " + name);
    }

    /** Puts the launcher's session back and stops using any proxy. */
    public static void restoreOriginal() {
        if (original == null) {
            return;
        }
        Reflect.set(Minecraft.getMinecraft(), Minecraft.class, Session.class, original, SESSION_FIELD);
        applyProxy(null);
        Log.info("Restored the original session (" + original.getUsername() + ")");
    }

    public static void apply(Account account, ProxyEntry proxy) {
        boolean offline = account.getType() == AccountType.OFFLINE;
        Session session = new Session(account.getUsername(), account.getStrippedUuid(),
                offline ? OFFLINE_TOKEN : account.getAccessToken(), offline ? "legacy" : "mojang");

        Reflect.set(Minecraft.getMinecraft(), Minecraft.class, Session.class, session, SESSION_FIELD);
        applyProxy(proxy);

        account.markUsed();
        Log.info("Switched session to " + account.getUsername() + " [" + account.getType().getLabel() + ", token "
                + Log.redact(account.getAccessToken()) + ", proxy " + (proxy == null ? "none" : proxy.describe()) + "]");
    }

    /**
     * Points the game's session service at the given proxy. The service is rebuilt rather than
     * mutated because authlib takes its proxy in the constructor, and the credentials are
     * handed to {@link ProxyAuthenticator} because those calls run on Minecraft's threads.
     */
    public static void applyProxy(ProxyEntry proxy) {
        Minecraft mc = Minecraft.getMinecraft();
        Proxy javaProxy = proxy == null ? Proxy.NO_PROXY : proxy.toJavaProxy();
        ProxyAuthenticator.setSessionProxy(proxy);

        MinecraftSessionService service = new YggdrasilAuthenticationService(javaProxy, UUID.randomUUID().toString())
                .createMinecraftSessionService();
        Reflect.set(mc, Minecraft.class, Proxy.class, javaProxy, PROXY_FIELD);
        Reflect.set(mc, Minecraft.class, MinecraftSessionService.class, service, SESSION_SERVICE_FIELD);
    }

    public static String currentUsername() {
        Session session = Minecraft.getMinecraft().getSession();
        return session == null ? "unknown" : session.getUsername();
    }
}
