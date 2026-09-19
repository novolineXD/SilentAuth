package dev.silentauth.net;

import dev.silentauth.SilentAuth;
import dev.silentauth.proxy.ProxyEntry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Http {

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;
    private static final String USER_AGENT = "SilentAuth/" + SilentAuth.VERSION;

    private Http() {
    }

    public static HttpResponse get(String url, ProxyEntry proxy, Map<String, String> headers) throws IOException {
        return execute("GET", url, proxy, headers, null, null);
    }

    public static HttpResponse postJson(String url, ProxyEntry proxy, Map<String, String> headers, String json)
            throws IOException {
        return execute("POST", url, proxy, headers, json.getBytes("UTF-8"), "application/json");
    }

    public static HttpResponse postForm(String url, ProxyEntry proxy, Map<String, String> headers,
                                        Map<String, String> form) throws IOException {
        return execute("POST", url, proxy, headers, encodeForm(form).getBytes("UTF-8"),
                "application/x-www-form-urlencoded");
    }

    public static Map<String, String> bearer(String token) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    private static HttpResponse execute(String method, String url, ProxyEntry proxy, Map<String, String> headers,
                                        byte[] body, String contentType) throws IOException {
        // Credentials go through the Authenticator rather than a Proxy-Authorization header:
        // on an HTTPS request that header would travel inside the tunnel to the destination.
        ProxyAuthenticator.bind(proxy);
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            connection = (HttpURLConnection) (proxy == null
                    ? target.openConnection()
                    : target.openConnection(proxy.toJavaProxy()));
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            if (body != null) {
                connection.setDoOutput(true);
                if (contentType != null) {
                    connection.setRequestProperty("Content-Type", contentType);
                }
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(body);
                    out.flush();
                } finally {
                    out.close();
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new HttpResponse(status, readFully(stream));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            ProxyAuthenticator.unbind();
        }
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), "UTF-8");
        } finally {
            stream.close();
        }
    }

    private static String encodeForm(Map<String, String> form) throws IOException {
        Map<String, String> ordered = new LinkedHashMap<String, String>(form);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return sb.toString();
    }
}
