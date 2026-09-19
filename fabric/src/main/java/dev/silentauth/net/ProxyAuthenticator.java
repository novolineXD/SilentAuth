package dev.silentauth.net;

import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Log;

import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Set;

/**
 * Feeds proxy credentials to the JDK.
 *
 * <p>Two sources, checked in that order. A thread local covers the mod's own calls, where the
 * proxy in use is known for the duration of one request. The session proxy covers everything
 * the game itself does afterwards - the session server calls made while joining a server run
 * on Minecraft's threads, not on ours, so without it an authenticated proxy would work for
 * the token check and then quietly fail at the join.</p>
 */
public final class ProxyAuthenticator extends Authenticator {

    private static final ThreadLocal<ProxyEntry> REQUEST = new ThreadLocal<ProxyEntry>();
    private static volatile ProxyEntry session;
    private static boolean installed;

    /**
     * Installs the authenticator and lifts the JDK's refusal to send Basic credentials on an
     * HTTPS CONNECT. Without this an HTTP proxy that needs a username and password cannot
     * tunnel to Mojang at all, which is otherwise only fixable with a launch argument.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        allowTunnelledBasicAuth();
        Authenticator.setDefault(new ProxyAuthenticator());
        installed = true;
    }

    /**
     * Java 8u111 and later refuse Basic proxy credentials on an HTTPS tunnel unless
     * {@code jdk.http.auth.tunneling.disabledSchemes} is cleared. Setting the property only
     * helps while the JDK has not read it yet, and by the time a mod loads the game has
     * usually made an HTTP call already and frozen the value into a static set. So set the
     * property for the not-yet-read case and empty the sets for the already-read one.
     */
    private static void allowTunnelledBasicAuth() {
        boolean cleared = true;
        for (String property : new String[] { "jdk.http.auth.tunneling.disabledSchemes",
                "jdk.http.auth.proxying.disabledSchemes" }) {
            try {
                System.setProperty(property, "");
            } catch (SecurityException e) {
                cleared = false;
            }
        }
        cleared &= emptyDisabledSchemes("disabledTunnelingSchemes");
        cleared &= emptyDisabledSchemes("disabledProxyingSchemes");
        if (!cleared) {
            Log.warn("Could not re-enable Basic proxy authentication. HTTP proxies that need a username and "
                    + "password will only work with -Djdk.http.auth.tunneling.disabledSchemes= on the command line");
        }
    }

    private static boolean emptyDisabledSchemes(String fieldName) {
        try {
            Field field = Class.forName("sun.net.www.protocol.http.HttpURLConnection")
                    .getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Set) {
                ((Set<?>) value).clear();
                return true;
            }
            return false;
        } catch (Throwable t) {
            // Not a JDK that has these, or they are locked down. The property above is the fallback.
            return false;
        }
    }

    /** Binds credentials for the calling thread, for the length of one request. */
    public static void bind(ProxyEntry proxy) {
        if (proxy == null || !proxy.hasCredentials()) {
            REQUEST.remove();
            return;
        }
        install();
        REQUEST.set(proxy);
    }

    public static void unbind() {
        REQUEST.remove();
    }

    /** Sets the proxy the game itself is now using, for calls the mod does not make. */
    public static void setSessionProxy(ProxyEntry proxy) {
        if (proxy != null && proxy.hasCredentials()) {
            install();
        }
        session = proxy;
    }

    static PasswordAuthentication credentialsFor(String host, int port, boolean proxyRequest) {
        PasswordAuthentication match = match(REQUEST.get(), host, port, proxyRequest);
        return match != null ? match : match(session, host, port, proxyRequest);
    }

    private static PasswordAuthentication match(ProxyEntry proxy, String host, int port, boolean proxyRequest) {
        if (proxy == null || !proxy.hasCredentials()) {
            return null;
        }
        // An HTTP proxy asks as a PROXY. A SOCKS proxy asks as a SERVER, so the endpoint has to
        // match before credentials go out, or they would leak to the destination host.
        boolean sameEndpoint = port == proxy.getPort() && host != null && host.equalsIgnoreCase(proxy.getHost());
        if (!proxyRequest && !sameEndpoint) {
            return null;
        }
        return new PasswordAuthentication(proxy.getUsername(), proxy.getPassword().toCharArray());
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        return credentialsFor(getRequestingHost(), getRequestingPort(), getRequestorType() == RequestorType.PROXY);
    }
}
