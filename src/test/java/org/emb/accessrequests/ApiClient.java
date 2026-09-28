package org.emb.accessrequests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A tiny HTTP client for tests that talks to the real server and carries the
 * bearer token by hand, the way the frontend does.
 */
public class ApiClient {

    public record Response(int status, JsonNode body, HttpResponse<String> raw) {

        public String errorCode() {
            return body.path("error").path("code").asString();
        }

        public String errorMessage() {
            return body.path("error").path("message").asString();
        }

        public List<String> setCookies() {
            return raw.headers().allValues("Set-Cookie");
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private String accessToken;

    public ApiClient(String baseUrl, JsonMapper jsonMapper) {
        this.baseUrl = baseUrl;
        this.jsonMapper = jsonMapper;
    }

    /** Logs in through {@code POST /api/auth/login} and keeps the access token. */
    public ApiClient login(String username) {
        Response response = post("/api/auth/login", "{\"username\":\"" + username + "\",\"password\":\"password123\"}");
        assertThat(response.status()).as("login as %s", username).isEqualTo(200);
        accessToken = response.body().path("accessToken").asString();
        return this;
    }

    public String accessToken() {
        return accessToken;
    }

    public Response get(String path) {
        return send(request(path).GET().build());
    }

    public Response post(String path, String json) {
        return post(path, "application/json", json);
    }

    public Response post(String path, String contentType, String body) {
        HttpRequest.Builder builder = request(path);
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return send(builder.build());
    }

    public Response send(String method, String path) {
        return send(request(path).method(method, HttpRequest.BodyPublishers.noBody()).build());
    }

    // Convenience calls for the workflow.

    public Response submit(long accessTypeId, String reason) {
        return post("/api/requests",
                jsonMapper.writeValueAsString(Map.of("accessTypeId", accessTypeId, "reason", reason)));
    }

    /** {@code comment} may be null, which sends {@code "comment": null}. */
    public Response decide(long requestId, String decision, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision);
        body.put("comment", comment);
        return post("/api/requests/" + requestId + "/decision", jsonMapper.writeValueAsString(body));
    }

    public Response reviewItems() {
        return get("/api/approvals");
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return builder;
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isEmpty()
                    ? jsonMapper.missingNode()
                    : jsonMapper.readTree(response.body());
            return new Response(response.statusCode(), body, response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
