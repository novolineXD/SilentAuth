package dev.silentauth.account;

import dev.silentauth.SilentAuth;
import dev.silentauth.auth.AuthException;
import dev.silentauth.auth.MicrosoftAuth;
import dev.silentauth.auth.SessionTokenAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Async;
import dev.silentauth.util.Log;
import net.minecraft.client.Minecraft;

public final class LoginService {

    public interface Callback {
        void onResult(boolean success, String message);
    }

    private LoginService() {
    }

    public static ProxyEntry resolveProxy(Account account) {
        if (account != null && !account.getProxyId().isEmpty()) {
            ProxyEntry bound = SilentAuth.proxies().byId(account.getProxyId());
            if (bound != null) {
                return bound;
            }
        }
        return SilentAuth.proxies().getDefault();
    }

    public static ProxyEntry resolveAuthProxy(Account account) {
        return SilentAuth.config().isProxyAuthRequests() ? resolveProxy(account) : null;
    }

    public static void loginAsync(final Account account, final Callback callback) {
        Async.run(new Runnable() {
            @Override
            public void run() {
                String message = login(account);
                boolean success = message == null;
                account.setStatus(success ? "ok" : message);
                if (callback != null) {
                    callback.onResult(success, success ? "Switched to " + account.getUsername() : message);
                }
            }
        });
    }

    private static String login(final Account account) {
        final ProxyEntry authProxy = resolveAuthProxy(account);

        if (account.getType() == AccountType.MICROSOFT && SilentAuth.config().isRefreshOnLogin()
                && (account.isTokenExpired() || !account.hasToken()) && account.canRefresh()) {
            try {
                MicrosoftAuth.refresh(account, authProxy);
                SilentAuth.accounts().save();
            } catch (AuthException e) {
                return "Token refresh failed: " + e.getMessage();
            }
        }

        if (account.getType().isOnline() && !account.hasToken()) {
            return "That account has no token stored";
        }

        final ProxyEntry sessionProxy = resolveProxy(account);
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                SessionSwapper.apply(account, sessionProxy);
                SilentAuth.accounts().setActive(account);
            }
        });
        return null;
    }

    public static void validateAsync(final Account account, final Callback callback) {
        Async.run(new Runnable() {
            @Override
            public void run() {
                if (account.getType() == AccountType.OFFLINE) {
                    account.setStatus("offline");
                    if (callback != null) {
                        callback.onResult(true, "Offline accounts need no check");
                    }
                    return;
                }
                if (account.canRefresh() && account.isTokenExpired()) {
                    try {
                        MicrosoftAuth.refresh(account, resolveAuthProxy(account));
                        SilentAuth.accounts().save();
                        if (callback != null) {
                            callback.onResult(true, account.getUsername() + " refreshed");
                        }
                        return;
                    } catch (AuthException e) {
                        account.setStatus(e.getMessage());
                        if (callback != null) {
                            callback.onResult(false, e.getMessage());
                        }
                        return;
                    }
                }
                boolean valid = SessionTokenAuth.validate(account, resolveAuthProxy(account));
                SilentAuth.accounts().save();
                if (callback != null) {
                    callback.onResult(valid, valid ? account.getUsername() + " is valid" : account.getStatus());
                }
            }
        });
    }

    public static void applyCurrentProxy() {
        Account active = SilentAuth.accounts().getActive();
        ProxyEntry proxy = resolveProxy(active);
        SessionSwapper.applyProxy(proxy);
        Log.info("Session service now uses " + (proxy == null ? "a direct connection" : proxy.describe()));
    }
}
