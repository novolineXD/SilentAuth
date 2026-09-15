package dev.silentauth.net;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.LoginService;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;
import dev.silentauth.util.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;

import java.io.IOException;

/**
 * Carries the real game connection through the active proxy, so the server sees the proxy's
 * IP rather than the player's.
 *
 * <p>Vanilla opens a direct socket to the server from {@link GuiConnecting}. This starts a
 * small local relay ({@link GameProxyRelay}) that tunnels through the proxy, then points the
 * game at {@code 127.0.0.1}. The relay rewrites the first handshake packet so the server still
 * receives its own hostname. Proxies resolve the destination themselves, so the DNS lookup
 * for the server does not leak either.</p>
 */
public final class ProxiedConnect {

    private ProxiedConnect() {
    }

    /**
     * Called as a connecting screen is about to open. If a proxy should carry the game
     * connection, this aborts the direct connect and returns a replacement screen that
     * connects through the relay; otherwise it returns null and the direct connect stands.
     */
    public static GuiScreen maybeReroute(GuiConnecting connecting) {
        // Reassigning GuiOpenEvent.gui does not post the event again, so our own relay
        // connection below never re-enters here and needs no guard.
        if (!SilentAuth.config().isRouteGameThroughProxy()) {
            return null;
        }
        ProxyEntry proxy = LoginService.resolveProxy(SilentAuth.accounts().getActive());
        if (proxy == null) {
            return null;
        }

        ServerData data = Minecraft.getMinecraft().getCurrentServerData();
        if (data == null || data.serverIP == null || data.serverIP.trim().isEmpty()) {
            return null;
        }
        ServerAddress address = ServerAddress.fromString(data.serverIP);
        String host = address.getIP();
        int port = address.getPort();
        if (host == null || host.isEmpty() || host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost")) {
            return null;
        }

        // Go back to whatever screen the direct connect would have returned to.
        GuiScreen parent = Reflect.get(connecting, GuiConnecting.class, GuiScreen.class,
                "previousGuiScreen", "field_146374_i");

        abortDirectConnect(connecting);
        try {
            GameProxyRelay relay = GameProxyRelay.start(proxy, host, port);
            Log.info("Routing " + host + ":" + port + " through " + proxy.describe());
            return new GuiConnecting(parent, Minecraft.getMinecraft(), "127.0.0.1", relay.getLocalPort());
        } catch (IOException e) {
            Log.error("Could not start the proxy relay, connecting directly", e);
            GameProxyRelay.stopActive();
            return null;
        }
    }

    /**
     * Stops the direct socket vanilla is opening. In the usual case its connect thread has not
     * run yet and the cancel flag makes it abort before touching the network; if it has already
     * opened a channel, that channel is closed.
     */
    private static void abortDirectConnect(GuiConnecting connecting) {
        try {
            Reflect.set(connecting, GuiConnecting.class, boolean.class, Boolean.TRUE, "cancel", "field_146373_h");
        } catch (RuntimeException e) {
            Log.warn("Could not cancel the direct connect: " + e.getMessage());
        }
        try {
            NetworkManager manager = Reflect.get(connecting, GuiConnecting.class, NetworkManager.class,
                    "networkManager", "field_146371_g");
            if (manager != null && manager.isChannelOpen()) {
                manager.closeChannel(new ChatComponentText("SilentAuth is routing this connection through a proxy"));
            }
        } catch (RuntimeException ignored) {
            // No channel yet, which is the normal and best case.
        }
    }
}
