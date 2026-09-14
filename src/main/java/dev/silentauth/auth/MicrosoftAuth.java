package dev.silentauth.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.silentauth.account.Account;
import dev.silentauth.account.AccountType;
import dev.silentauth.net.Http;
import dev.silentauth.net.HttpResponse;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Json;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MicrosoftAuth {

    public static final String DEFAULT_CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String LOGIN_WITH_XBOX_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

    private static String clientId = DEFAULT_CLIENT_ID;

    private MicrosoftAuth() {
    }

    public static void setClientId(String id) {
        clientId = id == null || id.trim().isEmpty() ? DEFAULT_CLIENT_ID : id.trim();
    }

    public static String getClientId() {
        return clientId;
    }

    public interface StatusListener {
        void onStatus(String message);
    }

    public static DeviceCode requestDeviceCode(ProxyEntry proxy) throws AuthException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", clientId);
        form.put("scope", SCOPE);
        try {
            HttpResponse response = Http.postForm(DEVICE_CODE_URL, proxy, null, form);
            if (!response.isOk()) {
                throw new AuthException("Microsoft refused the device code request: " + response.errorSummary());
            }
            JsonObject object = response.json();
            String deviceCode = Json.string(object, "device_code", "");
            String userCode = Json.string(object, "user_code", "");
            String uri = Json.string(object, "verification_uri", "https://microsoft.com/link");
            if (deviceCode.isEmpty() || userCode.isEmpty()) {
                throw new AuthException("Microsoft sent an incomplete device code response");
            }
            return new DeviceCode(deviceCode, userCode, uri, (int) Json.number(object, "interval", 5),
                    (int) Json.number(object, "expires_in", 900));
        } catch (IOException e) {
            throw new AuthException("Could not reach Microsoft: " + e.getMessage(), e);
        }
    }

    public static Account completeDeviceLogin(DeviceCode code, ProxyEntry proxy, AtomicBoolean cancelled,
                                              StatusListener listener) throws AuthException {
        String[] tokens = pollForToken(code, proxy, cancelled, listener);
        status(listener, "Signing in to Xbox Live");
        return finishLogin(tokens[0], tokens[1], proxy, listener);
    }

    public static boolean refresh(Account account, ProxyEntry proxy) throws AuthException {
        if (!account.canRefresh()) {
            return false;
        }
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", clientId);
        form.put("scope", SCOPE);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", account.getRefreshToken());
        try {
            HttpResponse response = Http.postForm(TOKEN_URL, proxy, null, form);
            if (!response.isOk()) {
                throw new AuthException("Refresh failed: " + response.errorSummary());
            }
            JsonObject object = response.json();
            String accessToken = Json.string(object, "access_token", "");
            String refreshToken = Json.string(object, "refresh_token", account.getRefreshToken());
            if (accessToken.isEmpty()) {
                throw new AuthException("Refresh response had no access token");
            }
            Account refreshed = finishLogin(accessToken, refreshToken, proxy, null);
            account.setUsername(refreshed.getUsername());
            account.setUuid(refreshed.getUuid());
            account.setAccessToken(refreshed.getAccessToken());
            account.setRefreshToken(refreshed.getRefreshToken());
            account.setTokenExpiresAt(refreshed.getTokenExpiresAt());
            account.setStatus("ok");
            return true;
        } catch (IOException e) {
            throw new AuthException("Could not reach Microsoft: " + e.getMessage(), e);
        }
    }

    private static String[] pollForToken(DeviceCode code, ProxyEntry proxy, AtomicBoolean cancelled,
                                         StatusListener listener) throws AuthException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", clientId);
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("device_code", code.getDeviceCode());

        long interval = code.getIntervalSeconds() * 1000L;
        while (true) {
            if (cancelled != null && cancelled.get()) {
                throw new AuthException("Cancelled");
            }
            if (code.isExpired()) {
                throw new AuthException("The code expired, start again");
            }
            sleep(interval, cancelled);

            HttpResponse response;
            try {
                response = Http.postForm(TOKEN_URL, proxy, null, form);
            } catch (IOException e) {
                throw new AuthException("Could not reach Microsoft: " + e.getMessage(), e);
            }

            JsonObject object = response.json();
            if (response.isOk()) {
                String accessToken = Json.string(object, "access_token", "");
                String refreshToken = Json.string(object, "refresh_token", "");
                if (accessToken.isEmpty()) {
                    throw new AuthException("Microsoft returned an empty access token");
                }
                return new String[] { accessToken, refreshToken };
            }

            String error = Json.string(object, "error", "");
            if (error.equals("authorization_pending")) {
                status(listener, "Waiting for the code to be entered");
                continue;
            }
            if (error.equals("slow_down")) {
                interval += 5000L;
                continue;
            }
            if (error.equals("authorization_declined")) {
                throw new AuthException("The sign in was declined");
            }
            if (error.equals("expired_token")) {
                throw new AuthException("The code expired, start again");
            }
            throw new AuthException("Microsoft rejected the login: " + response.errorSummary());
        }
    }

    private static Account finishLogin(String microsoftToken, String refreshToken, ProxyEntry proxy,
                                       StatusListener listener) throws AuthException {
        try {
            JsonObject xblProperties = new JsonObject();
            xblProperties.addProperty("AuthMethod", "RPS");
            xblProperties.addProperty("SiteName", "user.auth.xboxlive.com");
            xblProperties.addProperty("RpsTicket", "d=" + microsoftToken);
            JsonObject xblBody = new JsonObject();
            xblBody.add("Properties", xblProperties);
            xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
            xblBody.addProperty("TokenType", "JWT");

            HttpResponse xblResponse = Http.postJson(XBL_URL, proxy, jsonHeaders(), xblBody.toString());
            if (!xblResponse.isOk()) {
                throw new AuthException("Xbox Live rejected the token: " + xblResponse.errorSummary());
            }
            JsonObject xbl = xblResponse.json();
            String xblToken = Json.string(xbl, "Token", "");
            String userHash = readUserHash(xbl);
            if (xblToken.isEmpty() || userHash.isEmpty()) {
                throw new AuthException("Xbox Live sent an incomplete response");
            }

            status(listener, "Requesting an XSTS token");
            JsonArray userTokens = new JsonArray();
            userTokens.add(new com.google.gson.JsonPrimitive(xblToken));
            JsonObject xstsProperties = new JsonObject();
            xstsProperties.addProperty("SandboxId", "RETAIL");
            xstsProperties.add("UserTokens", userTokens);
            JsonObject xstsBody = new JsonObject();
            xstsBody.add("Properties", xstsProperties);
            xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            xstsBody.addProperty("TokenType", "JWT");

            HttpResponse xstsResponse = Http.postJson(XSTS_URL, proxy, jsonHeaders(), xstsBody.toString());
            if (xstsResponse.getStatus() == 401) {
                throw new AuthException(describeXstsError(xstsResponse.json()));
            }
            if (!xstsResponse.isOk()) {
                throw new AuthException("XSTS request failed: " + xstsResponse.errorSummary());
            }
            JsonObject xsts = xstsResponse.json();
            String xstsToken = Json.string(xsts, "Token", "");
            if (xstsToken.isEmpty()) {
                throw new AuthException("XSTS response had no token");
            }

            status(listener, "Exchanging the token with Mojang");
            JsonObject mcBody = new JsonObject();
            mcBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
            HttpResponse mcResponse = Http.postJson(LOGIN_WITH_XBOX_URL, proxy, jsonHeaders(), mcBody.toString());
            if (!mcResponse.isOk()) {
                throw new AuthException("Mojang rejected the Xbox token: " + mcResponse.errorSummary());
            }
            JsonObject mc = mcResponse.json();
            String accessToken = Json.string(mc, "access_token", "");
            long expiresIn = Json.number(mc, "expires_in", 86400L);
            if (accessToken.isEmpty()) {
                throw new AuthException("Mojang returned an empty access token");
            }

            status(listener, "Loading the profile");
            MinecraftServices.Profile profile = MinecraftServices.fetchProfile(accessToken, proxy);

            Account account = new Account(AccountType.MICROSOFT, profile.getName(), profile.getUuid(), accessToken);
            account.setRefreshToken(refreshToken);
            account.setTokenExpiresAt(System.currentTimeMillis() + (expiresIn * 1000L));
            account.setProxyId(proxy == null ? "" : proxy.getId());
            account.setStatus("ok");
            return account;
        } catch (IOException e) {
            throw new AuthException("Network error during sign in: " + e.getMessage(), e);
        }
    }

    private static String readUserHash(JsonObject xbl) {
        JsonObject claims = Json.object(xbl, "DisplayClaims");
        if (claims == null) {
            return "";
        }
        JsonArray xui = Json.array(claims, "xui");
        if (xui == null || xui.size() == 0 || !xui.get(0).isJsonObject()) {
            return "";
        }
        return Json.string(xui.get(0).getAsJsonObject(), "uhs", "");
    }

    private static String describeXstsError(JsonObject object) {
        long code = Json.number(object, "XErr", 0L);
        if (code == 2148916233L) {
            return "That Microsoft account has no Xbox profile yet";
        }
        if (code == 2148916235L) {
            return "Xbox Live is not available in that account's region";
        }
        if (code == 2148916236L || code == 2148916237L) {
            return "That account needs adult verification";
        }
        if (code == 2148916238L) {
            return "That account is a child account and needs to join a family";
        }
        return "XSTS refused the token (XErr " + code + ")";
    }

    private static Map<String, String> jsonHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/json");
        return headers;
    }

    private static void status(StatusListener listener, String message) {
        if (listener != null) {
            listener.onStatus(message);
        }
    }

    private static void sleep(long millis, AtomicBoolean cancelled) throws AuthException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (cancelled != null && cancelled.get()) {
                throw new AuthException("Cancelled");
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AuthException("Interrupted");
            }
        }
    }
}
