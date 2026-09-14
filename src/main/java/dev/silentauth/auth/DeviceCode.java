package dev.silentauth.auth;

public final class DeviceCode {

    private final String deviceCode;
    private final String userCode;
    private final String verificationUri;
    private final int intervalSeconds;
    private final long expiresAt;

    public DeviceCode(String deviceCode, String userCode, String verificationUri, int intervalSeconds, int expiresIn) {
        this.deviceCode = deviceCode;
        this.userCode = userCode;
        this.verificationUri = verificationUri;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getUserCode() {
        return userCode;
    }

    public String getVerificationUri() {
        return verificationUri;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public long getSecondsLeft() {
        return Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
    }
}
