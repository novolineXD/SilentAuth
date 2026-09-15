package dev.silentauth.net;

import dev.silentauth.proxy.ProxyEntry;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public final class ProxyAuthenticator extends Authenticator {

    private static final ThreadLocal<ProxyEntry> CURRENT = new ThreadLocal<ProxyEntry>();
    private static boolean installed;

    public static synchronized void install() {
        if (installed) {
            return;
        }
        Authenticator.setDefault(new ProxyAuthenticator());
        installed = true;
    }

    public static void bind(ProxyEntry proxy) {
        if (proxy == null || !proxy.hasCredentials()) {
            CURRENT.remove();
            return;
        }
        install();
        CURRENT.set(proxy);
    }

    public static void unbind() {
        CURRENT.remove();
    }

    public static PasswordAuthentication credentialsFor(String host, int port, boolean proxyRequest) {
        ProxyEntry proxy = CURRENT.get();
        if (proxy == null) {
            return null;
        }
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
