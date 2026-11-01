package com.bookbridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin HTTP wrapper so integration tests exercise the real servlet stack and
 * security filter chain instead of MockMvc shortcuts.
 */
final class ApiClient {
    static final JsonMapper JSON = JsonMapper.builder().build();

    record ApiCall(int status, JsonNode body, java.net.http.HttpHeaders headers) {
        String text(String field) {
            return body == null ? null : body.path(field).asString();
        }

        JsonNode data() {
            return body == null ? null : body.path("data");
        }
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;

    ApiClient(int port) {
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    ApiCall get(String path, String token) {
        return send("GET", path, null, token, null);
    }

    ApiCall get(String path, String token, String cookie) {
        return send("GET", path, null, token, cookie);
    }

    ApiCall post(String path, Object body, String token) {
        return send("POST", path, toJson(body), token, null);
    }

    ApiCall postWithCookie(String path, Object body, String cookie) {
        return send("POST", path, body == null ? "" : toJson(body), null, cookie);
    }

    ApiCall patch(String path, Object body, String token) {
        return send("PATCH", path, toJson(body), token, null);
    }

    ApiCall delete(String path, String token) {
        return send("DELETE", path, null, token, null);
    }

    String cookie(ApiCall call, String name) {
        return call.headers().firstValue("set-cookie")
                .map(value -> {
                    String prefix = name + "=";
                    int start = value.indexOf(prefix);
                    if (start < 0) {
                        return null;
                    }
                    int from = start + prefix.length();
                    int end = value.indexOf(';', from);
                    return end < 0 ? value.substring(from) : value.substring(from, end);
                })
                .orElse(null);
    }

    private String toJson(Object body) {
        return body == null ? null : JSON.writeValueAsString(body);
    }

    private ApiCall send(String method, String path, String body, String token, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15));
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        try {
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = response.body() == null || response.body().isBlank()
                    ? null
                    : JSON.readTree(response.body());
            return new ApiCall(response.statusCode(), parsed, response.headers());
        } catch (Exception ex) {
            throw new IllegalStateException(method + " " + path + " failed", ex);
        }
    }
}
