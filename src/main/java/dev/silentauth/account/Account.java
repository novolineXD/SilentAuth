package dev.silentauth.account;

import java.util.UUID;

public final class Account {

    private final String id;
    private AccountType type;
    private String username;
    private String uuid;
    private String accessToken;
    private String refreshToken;
    private long tokenExpiresAt;
    private String proxyId;
    private long addedAt;
    private long lastUsedAt;
    private volatile String status = "";

    public Account(AccountType type, String username, String uuid, String accessToken) {
        this(UUID.randomUUID().toString(), type, username, uuid, accessToken, "", 0L, "", System.currentTimeMillis(), 0L);
    }

    public Account(String id, AccountType type, String username, String uuid, String accessToken, String refreshToken,
                   long tokenExpiresAt, String proxyId, long addedAt, long lastUsedAt) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.type = type == null ? AccountType.SESSION : type;
        this.username = username == null ? "" : username;
        this.uuid = uuid == null ? "" : uuid;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.proxyId = proxyId == null ? "" : proxyId;
        this.addedAt = addedAt == 0L ? System.currentTimeMillis() : addedAt;
        this.lastUsedAt = lastUsedAt;
    }

    public static Account offline(String username) {
        Account account = new Account(AccountType.OFFLINE, username, offlineUuid(username), "");
        return account;
    }

    public static String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
    }

    public String getId() {
        return id;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid == null ? "" : uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken == null ? "" : refreshToken;
    }

    public long getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(long tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public String getProxyId() {
        return proxyId;
    }

    public void setProxyId(String proxyId) {
        this.proxyId = proxyId == null ? "" : proxyId;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public void markUsed() {
        this.lastUsedAt = System.currentTimeMillis();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public boolean hasToken() {
        return !accessToken.isEmpty();
    }

    public boolean isTokenExpired() {
        return tokenExpiresAt > 0L && System.currentTimeMillis() > tokenExpiresAt;
    }

    public boolean canRefresh() {
        return type == AccountType.MICROSOFT && !refreshToken.isEmpty();
    }

    public String getDashedUuid() {
        if (uuid.length() != 32) {
            return uuid;
        }
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-"
                + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }

    public String getStrippedUuid() {
        return uuid.replace("-", "");
    }
}
