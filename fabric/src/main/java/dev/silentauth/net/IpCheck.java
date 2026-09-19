package dev.silentauth.net;

import com.google.gson.JsonObject;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Json;

import java.io.IOException;

/**
 * Asks an echo service which IP a connection appears to come from, so a proxy's exit IP can be
 * compared with the real one. This is the same kind of TCP path the game connection takes, so
 * if the proxy changes the IP here it changes it for the server too.
 */
public final class IpCheck {

    private static final String ECHO_URL = "https://api.ipify.org/?format=json";

    private IpCheck() {
    }

    /**
     * The IP seen when going out through the given proxy, or directly when it is null.
     *
     * @throws IOException if the echo service could not be reached that way
     */
    public static String exitIp(ProxyEntry proxy) throws IOException {
        HttpResponse response = Http.get(ECHO_URL, proxy, null);
        if (!response.isOk()) {
            throw new IOException("echo service returned " + response.getStatus());
        }
        JsonObject object = response.json();
        String ip = Json.string(object, "ip", "");
        if (ip.isEmpty()) {
            throw new IOException("echo service gave no address");
        }
        return ip;
    }
}
