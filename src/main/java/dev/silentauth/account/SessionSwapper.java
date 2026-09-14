package dev.silentauth.account;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;
import dev.silentauth.util.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.net.Proxy;
import java.util.UUID;

public final class SessionSwapper {

    private static final String[] SESSION_FIELD = { "session", "field_71449_j" };
    private static final String[] SESSION_SERVICE_FIELD = { "sessionService", "field_152355_az" };
    private static final String[] PROXY_FIELD = { "proxy", "field_110453_aa" };

    private SessionSwapper() {
    }

    public static void apply(Account account, ProxyEntry proxy) {
        Minecraft mc = Minecraft.getMinecraft();
        String type = account.getType() == AccountType.OFFLINE ? "legacy" : "mojang";
        Session session = new Session(account.getUsername(), account.getStrippedUuid(), account.getAccessToken(), type);

        Reflect.set(mc, Minecraft.class, session, SESSION_FIELD);
        applyProxy(proxy);

        account.markUsed();
        Log.info("Switched session to " + account.getUsername() + " [" + account.getType().getLabel() + ", token "
                + Log.redact(account.getAccessToken()) + ", proxy " + (proxy == null ? "none" : proxy.describe()) + "]");
    }

    public static void applyProxy(ProxyEntry proxy) {
        Minecraft mc = Minecraft.getMinecraft();
        Proxy javaProxy = proxy == null ? Proxy.NO_PROXY : proxy.toJavaProxy();
        MinecraftSessionService service = new YggdrasilAuthenticationService(javaProxy, UUID.randomUUID().toString())
                .createMinecraftSessionService();
        Reflect.set(mc, Minecraft.class, javaProxy, PROXY_FIELD);
        Reflect.set(mc, Minecraft.class, service, SESSION_SERVICE_FIELD);
    }

    public static Session current() {
        return Minecraft.getMinecraft().getSession();
    }

    public static String currentUsername() {
        Session session = current();
        return session == null ? "unknown" : session.getUsername();
    }
}
