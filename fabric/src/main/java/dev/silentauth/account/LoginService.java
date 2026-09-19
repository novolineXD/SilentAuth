package dev.silentauth.account;

import dev.silentauth.SilentAuth;
import dev.silentauth.auth.AuthException;
import dev.silentauth.auth.MicrosoftAuth;
import dev.silentauth.auth.SessionTokenAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Async;
import dev.silentauth.util.Log;
import net.minecraft.client.MinecraftClient;

public final class LoginService {

    public interface Callback {
        void onResult(boolean success, String message);
    }

    private LoginService() {
    }

    /** The proxy an account's traffic should use: its own if it has one, otherwise the default. */
    public static ProxyEntry resolveProxy(Account account) {
        if (account != null && !account.getProxyId().isEmpty()) {
            ProxyEntry bound = SilentAuth.proxies().byId(account.getProxyId());
            if (bound != null) {
                return bound;
            }
        }
        return SilentAuth.proxies().getDefault();
    }

    /** The proxy for token and sign in calls, which the config can route directly instead. */
    public static ProxyEntry resolveAuthProxy(Account account) {
        return SilentAuth.config().isProxyAuthRequests() ? resolveProxy(account) : null;
    }

    public static void loginAsync(final Account account, final Callback callback) {
        Async.run(new Runnable() {
            @Override
            public void run() {
                String failure = prepare(account);
                if (failure != null) {
                    account.setValidity(Validity.INVALID, failure);
                    report(callback, false, failure);
                    return;
                }
                swapOnClientThread(account, callback);
            }
        });
    }

    /**
     * Gets the account ready to be switched to, refreshing an expired Microsoft token on the
     * way. Runs off the client thread.
     *
     * @return null when the account is ready, or the reason it is not
     */
    private static String prepare(Account account) {
        if (account.getType() == AccountType.MICROSOFT && SilentAuth.config().isRefreshOnLogin()
                && account.canRefresh() && (account.isTokenExpired() || !account.hasToken())) {
            try {
                MicrosoftAuth.refresh(account, resolveAuthProxy(account));
                SilentAuth.accounts().save();
            } catch (AuthException e) {
                return "Token refresh failed: " + e.getMessage();
            }
        }
        if (account.getType().isOnline() && !account.hasToken()) {
            return "That account has no token stored";
        }
        return null;
    }

    /** The swap itself touches Minecraft state, so it is scheduled back onto the client thread. */
    private static void swapOnClientThread(final Account account, final Callback callback) {
        final ProxyEntry sessionProxy = resolveProxy(account);
        MinecraftClient.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SessionSwapper.apply(account, sessionProxy);
                    SilentAuth.accounts().setActive(account);
                    account.setValidity(Validity.VALID, "");
                    report(callback, true, "Playing as " + account.getUsername());
                } catch (RuntimeException e) {
                    Log.error("Could not swap the session", e);
                    String message = "Could not swap the session: " + e.getMessage();
                    account.setValidity(Validity.INVALID, message);
                    report(callback, false, message);
                }
            }
        });
    }

    /**
     * Checks one account's token, refreshing a stale Microsoft one first. Runs on the calling
     * thread, so callers that are on the client thread should hand this to {@link AccountChecker}.
     */
    static void check(Account account) {
        if (account.getType() == AccountType.OFFLINE) {
            account.setValidity(Validity.VALID, "");
            return;
        }
        if (!account.hasToken()) {
            account.setValidity(Validity.INVALID, "no token stored");
            return;
        }
        if (account.canRefresh() && account.isTokenExpired()) {
            try {
                MicrosoftAuth.refresh(account, resolveAuthProxy(account));
                SilentAuth.accounts().save();
            } catch (AuthException e) {
                account.setValidity(Validity.INVALID, e.getMessage());
            }
            return;
        }
        SessionTokenAuth.validate(account, resolveAuthProxy(account));
        SilentAuth.accounts().save();
    }

    /** Re-points the api services at whatever proxy the active account should be using. */
    public static void applyCurrentProxy() {
        final ProxyEntry proxy = resolveProxy(SilentAuth.accounts().getActive());
        MinecraftClient.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                SessionSwapper.applyProxy(proxy);
                Log.info("Api services now use " + (proxy == null ? "a direct connection" : proxy.describe()));
            }
        });
    }

    private static void report(Callback callback, boolean success, String message) {
        if (callback != null) {
            callback.onResult(success, message);
        }
    }
}
