package dev.silentauth.net;

import dev.silentauth.proxy.ProxyEntry;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public final class ProxyAuthenticator extends Authenticator {

    private static final ThreadLocal<PasswordAuthentication> CURRENT = new ThreadLocal<PasswordAuthentication>();
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
        CURRENT.set(new PasswordAuthentication(proxy.getUsername(), proxy.getPassword().toCharArray()));
    }

    public static void unbind() {
        CURRENT.remove();
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() != RequestorType.PROXY) {
            return null;
        }
        return CURRENT.get();
    }
}
