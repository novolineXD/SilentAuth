package dev.silentauth.net;

import com.google.gson.JsonObject;
import dev.silentauth.util.Json;

public final class HttpResponse {

    private final int status;
    private final String body;

    public HttpResponse(int status, String body) {
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public boolean isOk() {
        return status >= 200 && status < 300;
    }

    public JsonObject json() {
        return Json.parseObject(body);
    }

    public String errorSummary() {
        JsonObject object = json();
        String error = Json.string(object, "error", "");
        String description = Json.string(object, "error_description", "");
        String message = Json.string(object, "errorMessage", "");
        if (!description.isEmpty()) {
            return description;
        }
        if (!message.isEmpty()) {
            return message;
        }
        if (!error.isEmpty()) {
            return error;
        }
        return "HTTP " + status;
    }
}
