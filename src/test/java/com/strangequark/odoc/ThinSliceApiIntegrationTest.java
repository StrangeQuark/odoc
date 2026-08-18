package com.strangequark.odoc;

import static org.assertj.core.api.Assertions.assertThat;

import com.strangequark.odoc.thinslice.ThinSliceApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** Proves the temporary Phase 0 vertical-slice command is validated and idempotent end to end. */
@SpringBootTest(classes = ThinSliceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "thin-slice"})
class ThinSliceApiIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private ApplicationContext context;

    @Test
    void deliberatelyStartsWithoutDataOrProductInfrastructure() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.containsBean("pageService")).isFalse();
        assertThat(context.containsBean("spaceService")).isFalse();
        assertThat(context.containsBean("pageController")).isFalse();
        assertThat(context.containsBean("spaceController")).isFalse();
        assertThat(context.containsBean("mediaAssetOrphanCleanup")).isFalse();
    }

    @Test
    void launchSelectionUsesTheMinimalApplicationOnlyForTheExplicitProfile() {
        assertThat(OdocApplication.applicationSource(new String[] {"--spring.profiles.active=local,thin-slice"}))
                .isEqualTo(ThinSliceApplication.class);
        assertThat(OdocApplication.applicationSource(new String[] {"--spring.profiles.active=local"}))
                .isEqualTo(OdocApplication.class);
    }

    @Test
    void validatesAndReplaysTheTestOnlyCommand() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String credentials = Base64.getEncoder().encodeToString("developer:developer".getBytes(StandardCharsets.UTF_8));
        String commandUrl = url("/api/v1/test/commands/echo");

        HttpResponse<String> invalid = send(client, commandUrl, credentials, "phase0-key", "{\"message\":\"\"}");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("Request validation failed.").contains("\"errors\"");

        HttpResponse<String> first = send(client, commandUrl, credentials, "phase0-key", "{\"message\":\"phase zero\"}");
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");

        HttpResponse<String> replay = send(client, commandUrl, credentials, "phase0-key", "{\"message\":\"phase zero\"}");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.headers().firstValue("Idempotency-Replayed")).contains("true");
        assertThat(replay.body()).isEqualTo(first.body());

        HttpResponse<String> conflict = send(client, commandUrl, credentials, "phase0-key", "{\"message\":\"different\"}");
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("Idempotency-Key was already used with a different request.");
    }

    @Test
    void generatedThinSliceOpenApiMatchesTheCommittedProfileContract() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(url("/v3/api-docs"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        String generated = response.body().replace("http://localhost:" + port, "http://localhost:8080");
        String committed = Files.readString(Path.of("openapi/odoc-thin-slice-v1.json"));
        assertThat(canonicalJson(generated)).isEqualTo(canonicalJson(committed));
    }

    private HttpResponse<String> send(
            HttpClient client, String target, String credentials, String idempotencyKey, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(target))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-Id", "thin-slice-contract-test")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String canonicalJson(String value) throws Exception {
        return JSON.writeValueAsString(sorted(JSON.readTree(value)));
    }

    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            node.properties().stream()
                    .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                    .forEach(entry -> sorted.set(entry.getKey(), sorted(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JSON.createArrayNode();
            node.forEach(item -> sorted.add(sorted(item)));
            return sorted;
        }
        return node;
    }
}
