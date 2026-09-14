package dev.silentauth.auth;

import com.google.gson.JsonObject;
import dev.silentauth.net.Http;
import dev.silentauth.net.HttpResponse;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Json;

import java.io.IOException;

public final class MinecraftServices {

    public static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    public static final String ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    private MinecraftServices() {
    }

    public static Profile fetchProfile(String accessToken, ProxyEntry proxy) throws AuthException {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new AuthException("No token was given");
        }
        HttpResponse response;
        try {
            response = Http.get(PROFILE_URL, proxy, Http.bearer(accessToken.trim()));
        } catch (IOException e) {
            throw new AuthException("Could not reach api.minecraftservices.com: " + e.getMessage(), e);
        }

        if (response.getStatus() == 401) {
            throw new AuthException("The token was rejected, it is expired or invalid");
        }
        if (response.getStatus() == 404) {
            throw new AuthException("That account does not own Minecraft");
        }
        if (!response.isOk()) {
            throw new AuthException("Profile lookup failed: " + response.errorSummary());
        }

        JsonObject object = response.json();
        String id = Json.string(object, "id", "");
        String name = Json.string(object, "name", "");
        if (id.isEmpty() || name.isEmpty()) {
            throw new AuthException("Profile response did not contain a name and uuid");
        }
        return new Profile(id, name);
    }

    public static final class Profile {

        private final String uuid;
        private final String name;

        public Profile(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }
    }
}
