package dev.silentauth.auth;

import dev.silentauth.account.Account;
import dev.silentauth.account.AccountType;
import dev.silentauth.account.Validity;
import dev.silentauth.proxy.ProxyEntry;

public final class SessionTokenAuth {

    private SessionTokenAuth() {
    }

    public static Account login(String raw, ProxyEntry proxy) throws AuthException {
        String token = normalise(raw);
        MinecraftServices.Profile profile = MinecraftServices.fetchProfile(token, proxy);
        Account account = new Account(AccountType.SESSION, profile.getName(), profile.getUuid(), token);
        account.setValidity(Validity.VALID, "");
        return account;
    }

    public static Account withoutLookup(String raw, String username, String uuid) throws AuthException {
        String token = normalise(raw);
        if (username == null || username.trim().isEmpty()) {
            throw new AuthException("A username is required when the profile lookup is skipped");
        }
        String cleanedUuid = uuid == null ? "" : uuid.trim().replace("-", "");
        if (cleanedUuid.isEmpty()) {
            throw new AuthException("A uuid is required when the profile lookup is skipped");
        }
        Account account = new Account(AccountType.SESSION, username.trim(), cleanedUuid, token);
        account.setValidity(Validity.UNKNOWN, "");
        return account;
    }

    public static boolean validate(Account account, ProxyEntry proxy) {
        try {
            MinecraftServices.Profile profile = MinecraftServices.fetchProfile(account.getAccessToken(), proxy);
            account.setUsername(profile.getName());
            account.setUuid(profile.getUuid());
            account.setValidity(Validity.VALID, "");
            return true;
        } catch (AuthException e) {
            account.setValidity(Validity.INVALID, e.getMessage());
            return false;
        }
    }

    private static String normalise(String raw) throws AuthException {
        if (raw == null) {
            throw new AuthException("No token was given");
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new AuthException("No token was given");
        }
        if (value.toLowerCase().startsWith("token:")) {
            String[] parts = value.split(":");
            if (parts.length >= 2) {
                value = parts[1];
            }
        }
        if (value.toLowerCase().startsWith("bearer ")) {
            value = value.substring(7).trim();
        }
        if (value.contains(" ") || value.length() < 16) {
            throw new AuthException("That does not look like a session token");
        }
        return value;
    }
}
