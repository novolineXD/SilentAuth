package dev.silentauth.fabric;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.LoginService;
import dev.silentauth.net.GameProxyRelay;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;

/**
 * Carries the real game connection through the active proxy, so the server sees the proxy's IP
 * rather than the player's. Every join funnels through {@link ConnectScreen#connect}, so a single
 * mixin there hands the address to {@link #reroute}; server-list pings do not go through that
 * method, so they are left alone.
 *
 * <p>The socket is pointed at a local relay, which tunnels through the proxy and rewrites the first
 * handshake packet so the server still receives its own hostname. The proxy resolves the
 * destination, so the DNS lookup does not leak either. This is the Fabric counterpart of the 1.8.9
 * {@code ProxiedConnect}.</p>
 */
public final class ProxiedJoin {

    /** Set while we start our own routed connect, so the mixin does not intercept it again. */
    private static volatile boolean ours;

    private ProxiedJoin() {
    }

    /**
     * Reroutes a pending join through the active proxy.
     *
     * @return true when a routed connection was started and the original join should be cancelled,
     *         false to let vanilla connect directly
     */
    public static boolean reroute(Screen screen, MinecraftClient client, ServerAddress address,
                                  ServerInfo info, boolean quickPlay, CookieStorage cookieStorage) {
        if (ours) {
            return false;
        }
        if (!SilentAuth.config().isRouteGameThroughProxy()) {
            return false;
        }
        ProxyEntry proxy = LoginService.resolveProxy(SilentAuth.accounts().getActive());
        if (proxy == null) {
            return false;
        }
        String host = address.getAddress();
        int port = address.getPort();
        if (host == null || host.isEmpty() || host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost")) {
            return false;
        }
        try {
            GameProxyRelay relay = GameProxyRelay.start(proxy, host, port);
            Log.info("Routing " + host + ":" + port + " through " + proxy.describe());
            ServerAddress local = new ServerAddress("127.0.0.1", relay.getLocalPort());
            ours = true;
            try {
                ConnectScreen.connect(screen, client, local, info, quickPlay, cookieStorage);
            } finally {
                ours = false;
            }
            return true;
        } catch (IOException e) {
            Log.error("Could not start the proxy relay, connecting directly", e);
            GameProxyRelay.stopActive();
            return false;
        }
    }
}
