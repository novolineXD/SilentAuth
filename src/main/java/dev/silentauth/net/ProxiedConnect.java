package dev.silentauth.net;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.LoginService;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;
import dev.silentauth.util.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;

/**
 * Carries the real game connection through the active proxy, so the server sees the proxy's
 * IP rather than the player's.
 *
 * <p>Vanilla opens a direct socket to the server from {@link GuiConnecting}, and its connect
 * thread only checks its cancel flag once, before opening that socket. Cancelling after the
 * fact therefore races the socket - so the reliable paths ({@link #connect} and the Join
 * button) start the proxied connection themselves and never let vanilla build a direct one.
 * The {@link #maybeReroute} hook is a best-effort catch for the paths that cannot be
 * intercepted before construction (double click, LAN); it wins the race in practice but is not
 * guaranteed, which is why the guaranteed paths exist.</p>
 *
 * <p>The relay tunnels through the proxy and rewrites the first handshake packet so the server
 * still receives its own hostname. Proxies resolve the destination themselves, so the DNS
 * lookup does not leak either.</p>
 */
public final class ProxiedConnect {

    /** Set while we open our own relay connection, so it is not intercepted as if it were vanilla's. */
    private static volatile boolean ours;

    private ProxiedConnect() {
    }

    /**
     * Joins a server through the active proxy, race free: no vanilla direct connection is ever
     * created. Falls back to a direct connect when no proxy is in use. Must run on the client
     * thread.
     *
     * @param rawAddress a {@code host} or {@code host:port} as typed by the user
     */
    public static void connect(GuiScreen parent, String rawAddress) {
        Minecraft mc = Minecraft.getMinecraft();
        ServerAddress address = ServerAddress.fromString(rawAddress);
        String host = address.getIP();
        int port = address.getPort();

        ProxyEntry proxy = LoginService.resolveProxy(SilentAuth.accounts().getActive());
        if (proxy == null || !SilentAuth.config().isRouteGameThroughProxy()) {
            show(new GuiConnecting(parent, mc, host, port));
            return;
        }
        try {
            GameProxyRelay relay = GameProxyRelay.start(proxy, host, port);
            Log.info("Routing " + host + ":" + port + " through " + proxy.describe());
            show(new GuiConnecting(parent, mc, "127.0.0.1", relay.getLocalPort()));
        } catch (IOException e) {
            Log.error("Could not start the proxy relay, connecting directly", e);
            GameProxyRelay.stopActive();
            show(new GuiConnecting(parent, mc, host, port));
        }
    }

    /** Shows a connecting screen we built, without {@link #maybeReroute} intercepting it. */
    private static void show(GuiConnecting connecting) {
        ours = true;
        Minecraft.getMinecraft().displayGuiScreen(connecting);
        ours = false;
    }

    /** The IP of the selected server on the multiplayer screen, or null if nothing usable is picked. */
    public static String selectedServerIp(GuiMultiplayer gui) {
        try {
            ServerSelectionList list = Reflect.get(gui, GuiMultiplayer.class, ServerSelectionList.class,
                    "serverListSelector", "field_146803_h");
            int index = list.func_148193_k();
            if (index < 0) {
                return null;
            }
            GuiListExtended.IGuiListEntry entry = list.getListEntry(index);
            if (!(entry instanceof ServerListEntryNormal)) {
                return null;
            }
            ServerData data = ((ServerListEntryNormal) entry).getServerData();
            return data == null ? null : data.serverIP;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Best-effort reroute for a connecting screen we did not create ourselves. Returns a
     * replacement that goes through the proxy, or null to leave the direct connect alone.
     */
    public static GuiScreen maybeReroute(GuiConnecting connecting) {
        if (ours) {
            // Our own relay connection from show(), let it through untouched.
            return null;
        }
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

        GuiScreen parent = Reflect.get(connecting, GuiConnecting.class, GuiScreen.class,
                "previousGuiScreen", "field_146374_i");
        abortDirectConnect(connecting);
        try {
            GameProxyRelay relay = GameProxyRelay.start(proxy, host, port);
            Log.info("Routing " + host + ":" + port + " through " + proxy.describe()
                    + " (best effort; use the Join button or /sa join to be sure)");
            return new GuiConnecting(parent, Minecraft.getMinecraft(), "127.0.0.1", relay.getLocalPort());
        } catch (IOException e) {
            Log.error("Could not start the proxy relay, connecting directly", e);
            GameProxyRelay.stopActive();
            return null;
        }
    }

    private static void abortDirectConnect(GuiConnecting connecting) {
        try {
            Reflect.set(connecting, GuiConnecting.class, boolean.class, Boolean.TRUE, "cancel", "field_146373_h");
        } catch (RuntimeException e) {
            Log.warn("Could not cancel the direct connect: " + e.getMessage());
        }
        try {
            net.minecraft.network.NetworkManager manager = Reflect.get(connecting, GuiConnecting.class,
                    net.minecraft.network.NetworkManager.class, "networkManager", "field_146371_g");
            if (manager != null && manager.isChannelOpen()) {
                manager.closeChannel(new net.minecraft.util.ChatComponentText("SilentAuth reroute"));
            }
        } catch (RuntimeException ignored) {
            // No channel yet, which is the normal and best case.
        }
    }
}
