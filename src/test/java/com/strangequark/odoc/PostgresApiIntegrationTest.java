package com.strangequark.odoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.strangequark.odoc.encryption.EncryptedRecord;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises Flyway, HTTP routing, OpenAPI, request IDs, and Problem Details against PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Execution(ExecutionMode.SAME_THREAD)
class PostgresApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine")
            .withDatabaseName("odoc")
            .withUsername("odoc")
            .withPassword("odoc");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("odoc.database.statement-timeout", () -> "300ms");
        registry.add("odoc.database.lock-timeout", () -> "100ms");
        registry.add("odoc.database.idle-in-transaction-timeout", () -> "250ms");
        registry.add("odoc.encryption.managed.enabled", () -> "true");
        registry.add("odoc.encryption.managed.wrapping-key-base64", () ->
                Base64.getEncoder().encodeToString(new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                    17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
                }));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ManagedRecordEncryption managedRecordEncryption;

    @Test
    void servesOpenApiAndReturnsCorrelatedProblemDetails() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> openApi = client.send(
                HttpRequest.newBuilder(URI.create(url("/v3/api-docs"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(openApi.statusCode()).isEqualTo(200);
        assertThat(openApi.body()).contains("Odoc API");
        assertThat(openApi.body()).doesNotContain("/api/v1/test/commands/echo");
        assertThat(normalizeEphemeralServerPort(openApi.body()))
                .isEqualTo(Files.readString(Path.of("openapi", "odoc-v1.json")).stripTrailing());

        String basicCredentials = Base64.getEncoder().encodeToString("developer:developer".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> invalidSpace = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/spaces")))
                .header("Authorization", "Basic " + basicCredentials)
                .header("Content-Type", "application/json")
                .header("X-Request-Id", "phase0-contract-test")
                .POST(HttpRequest.BodyPublishers.ofString("{\"key\":\"\",\"name\":\"\",\"description\":\"\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(invalidSpace.statusCode()).isEqualTo(400);
        assertThat(invalidSpace.headers().firstValue("X-Request-Id")).contains("phase0-contract-test");
        assertThat(invalidSpace.body()).contains("\"requestId\":\"phase0-contract-test\"");
        assertThat(invalidSpace.body()).contains("\"errors\"");

        HttpResponse<String> malformedJson = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/spaces")))
                .header("Authorization", "Basic " + basicCredentials)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(malformedJson.statusCode()).isEqualTo(400);
        assertThat(malformedJson.body()).contains("Request body must be valid JSON.");

        HttpResponse<String> unauthenticated = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/spaces")))
                .header("X-Request-Id", "unauthenticated-contract-test")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(unauthenticated.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/problem+json"));
        assertThat(unauthenticated.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(unauthenticated.body())
                .contains("\"status\":401")
                .contains("\"requestId\":\"unauthenticated-contract-test\"");
    }

    @Test
    void exposesIndependentLivenessAndANoStoreTypedSystemStatus() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> liveness = client.send(
                HttpRequest.newBuilder(URI.create(url("/actuator/health/liveness"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).contains("\"status\":\"UP\"");

        HttpResponse<String> systemInfo = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/system/info"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(systemInfo.statusCode()).isEqualTo(200);
        assertThat(systemInfo.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(systemInfo.body())
                .contains("\"name\":\"Odoc\"")
                .contains("\"status\":\"ok\"")
                .contains("\"timestamp\"");
    }

    @Test
    void appliesBoundedPostgresSessionSettingsAndRejectsLockAndQueryStalls() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(setting(connection, "TIME ZONE")).isEqualTo("UTC");
            assertThat(setting(connection, "search_path")).isEqualTo("public");
            assertThat(setting(connection, "statement_timeout")).isEqualTo("300ms");
            assertThat(setting(connection, "lock_timeout")).isEqualTo("100ms");
            assertThat(setting(connection, "idle_in_transaction_session_timeout")).isEqualTo("250ms");

            assertThatThrownBy(() -> execute(connection, "SELECT pg_sleep(0.5)"))
                    .hasMessageContaining("statement timeout");
        }

        try (Connection lockHolder = dataSource.getConnection(); Connection blocked = dataSource.getConnection()) {
            lockHolder.setAutoCommit(false);
            execute(lockHolder, "LOCK TABLE spaces IN ACCESS EXCLUSIVE MODE");
            assertThatThrownBy(() -> execute(blocked, "SELECT count(*) FROM spaces"))
                    .hasMessageContaining("lock timeout");
            lockHolder.rollback();
        }
    }

    @Test
    void terminatesIdleTransactionsInsteadOfLettingPoolConnectionsLeak() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            execute(connection, "SELECT 1");

            // The test profile configures a deliberately short timeout. A connection left inside a
            // transaction must be terminated by PostgreSQL rather than consuming a pool slot forever.
            Thread.sleep(450);

            assertThat(connection.isValid(1)).isFalse();
            assertThatThrownBy(() -> execute(connection, "SELECT 1"))
                    .satisfies(error -> assertThat(error.getMessage())
                            .containsAnyOf("closed", "connection", "I/O", "terminating connection"));
        }
    }

    @Test
    void runtimeDatabaseRoleCannotAlterTheSchema() throws Exception {
        String runtimeRole = "odoc_runtime_phase_one_test";
        String runtimePassword = "phase-one-runtime-password";
        try (Connection owner = dataSource.getConnection()) {
            execute(owner, "CREATE ROLE " + runtimeRole + " LOGIN PASSWORD '" + runtimePassword + "'");
            execute(owner, "GRANT CONNECT ON DATABASE odoc TO " + runtimeRole);
            execute(owner, "GRANT USAGE ON SCHEMA public TO " + runtimeRole);
            execute(owner, "GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + runtimeRole);

            try (Connection runtime = DriverManager.getConnection(POSTGRES.getJdbcUrl(), runtimeRole, runtimePassword)) {
                assertThat(setting(runtime, "search_path")).isEqualTo("\"$user\", public");
                assertThatThrownBy(() -> execute(runtime, "CREATE TABLE runtime_must_not_create (id integer)"))
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> execute(runtime, "ALTER TABLE spaces ADD COLUMN forbidden integer"))
                        .hasMessageContaining("must be owner");
                execute(runtime, "SELECT count(*) FROM spaces");
            }
        } finally {
            try (Connection owner = dataSource.getConnection()) {
                execute(owner, "DROP OWNED BY " + runtimeRole);
                execute(owner, "DROP ROLE IF EXISTS " + runtimeRole);
            }
        }
    }

    @Test
    void staleOptimisticRevisionCannotOverwriteAConcurrentUpdate() throws Exception {
        UUID spaceId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement create = connection.prepareStatement(
                    "INSERT INTO spaces (id, workspace_id, space_key, name, description, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                create.setObject(1, spaceId);
                create.setObject(2, UUID.fromString("00000000-0000-0000-0000-000000000014"));
                create.setString(3, "optimistic-" + spaceId);
                create.setString(4, "Optimistic concurrency");
                assertThat(create.executeUpdate()).isOne();
            }
        }

        try (Connection firstWriter = dataSource.getConnection(); Connection staleWriter = dataSource.getConnection()) {
            assertThat(updateNameWhenRevisionMatches(firstWriter, spaceId, "First writer", 0)).isOne();
            assertThat(updateNameWhenRevisionMatches(staleWriter, spaceId, "Stale writer", 0)).isZero();
            assertThat(updateNameWhenRevisionMatches(staleWriter, spaceId, "Fresh writer", 1)).isOne();
        }
    }

    @Test
    void persistsOnlyWrappedPurposeScopedDataKeys() throws Exception {
        UUID scopeId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        byte[] plaintext = "classified-identity-canary@example.test".getBytes(StandardCharsets.UTF_8);
        EncryptionContext context = new EncryptionContext(
                new SecurityScope(SecurityScopeKind.INSTANCE, scopeId), resourceId, EncryptionPurpose.IDENTITY, 1);

        EncryptedRecord record = managedRecordEncryption.encrypt(context, plaintext);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT wrapped_dek FROM managed_data_keys WHERE scope_id = ? AND purpose = 'IDENTITY'")) {
            query.setObject(1, scopeId);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBytes(1)).isNotEqualTo(plaintext);
                assertThat(result.next()).isFalse();
            }
        }
        assertThat(managedRecordEncryption.decrypt(context, record)).isEqualTo(plaintext);
    }

    @Test
    void registersAndAuthenticatesALocalPasswordAccountWithCookieCsrfProtection() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String email = "member-" + UUID.randomUUID() + "@example.test";
        String password = "correct-horse-battery-staple";

        HttpResponse<String> registration = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/register")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(registration.statusCode()).isEqualTo(201);
        assertThat(registration.body()).contains(email);
        assertThat(registration.body()).contains("\"emailVerified\":false");
        assertThat(registration.headers().allValues("Set-Cookie")).anySatisfy(cookie -> {
            assertThat(cookie).contains("ODOC_SESSION=").contains("HttpOnly").contains("SameSite=Strict");
        });

        String csrf = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("ODOC_CSRF"))
                .findFirst()
                .orElseThrow()
                .getValue();

        assertThat(registration.body()).doesNotContain("developmentVerificationToken");
        HttpResponse<String> resentVerification = client.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/auth/email-verification/resend")))
                .header("X-Odoc-Csrf", csrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(resentVerification.statusCode()).isEqualTo(204);
        assertThat(resentVerification.body()).isBlank();

        HttpResponse<String> session = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/auth/session"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(session.body()).contains(email);
        assertThat(session.body()).contains("\"emailVerified\":false");

        HttpResponse<String> wrongCurrentPassword = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/password")))
                .header("Content-Type", "application/json")
                .header("X-Odoc-Csrf", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-correct-horse-battery\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(wrongCurrentPassword.statusCode()).isEqualTo(400);

        String changedPassword = "new-correct-horse-battery";
        HttpResponse<String> changedPasswordResponse = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/password")))
                .header("Content-Type", "application/json")
                .header("X-Odoc-Csrf", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"currentPassword\":\"" + password + "\",\"newPassword\":\"" + changedPassword + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(changedPasswordResponse.statusCode()).isEqualTo(200);
        assertThat(changedPasswordResponse.body()).contains("\"emailVerified\":false");
        csrf = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("ODOC_CSRF"))
                .findFirst()
                .orElseThrow()
                .getValue();

        HttpClient passwordLoginClient = HttpClient.newHttpClient();
        HttpResponse<String> oldPasswordLogin = passwordLoginClient.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(oldPasswordLogin.statusCode()).isEqualTo(401);
        HttpResponse<String> newPasswordLogin = passwordLoginClient.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"" + changedPassword + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(newPasswordLogin.statusCode()).isEqualTo(200);

        HttpResponse<String> missingCsrf = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/spaces")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"key\":\"local-auth\",\"name\":\"Local auth\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        assertThat(missingCsrf.headers().firstValue("Content-Type")).hasValueSatisfying(value ->
                assertThat(value).contains("application/problem+json"));

        HttpResponse<String> mutation = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/spaces")))
                .header("Content-Type", "application/json")
                .header("X-Odoc-Csrf", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"key\":\"local-auth\",\"name\":\"Local auth\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(mutation.statusCode()).isEqualTo(201);
        UUID ownerSpaceId = UUID.fromString(mutation.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));
        String privateTitle = "Owner-only-" + UUID.randomUUID();
        HttpResponse<String> privatePage = client.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/spaces/" + ownerSpaceId + "/pages")))
                .header("Content-Type", "application/json")
                .header("X-Odoc-Csrf", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"title\":\"" + privateTitle + "\",\"content\":\"Private workspace content\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(privatePage.statusCode()).isEqualTo(201);
        UUID ownerPageId = UUID.fromString(privatePage.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));

        HttpResponse<String> ownerWorkspaces = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/workspaces"))).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(ownerWorkspaces.statusCode()).isEqualTo(200);
        UUID ownerWorkspaceId = UUID.fromString(ownerWorkspaces.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));

        CookieManager otherCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient otherClient = HttpClient.newBuilder().cookieHandler(otherCookies).build();
        String otherEmail = "other-" + UUID.randomUUID() + "@example.test";
        HttpResponse<String> otherRegistration = otherClient.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/register")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + otherEmail + "\",\"password\":\"" + password + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(otherRegistration.statusCode()).isEqualTo(201);

        HttpResponse<String> isolatedSpaces = otherClient.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/spaces"))).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(isolatedSpaces.statusCode()).isEqualTo(200);
        assertThat(isolatedSpaces.body()).doesNotContain(ownerSpaceId.toString());

        HttpResponse<String> inaccessiblePages = otherClient.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/spaces/" + ownerSpaceId + "/pages"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(inaccessiblePages.statusCode()).isEqualTo(404);

        for (String privatePath : List.of(
                "/api/v1/pages/" + ownerPageId,
                "/api/v1/pages/" + ownerPageId + "/comments",
                "/api/v1/pages/" + ownerPageId + "/favorite",
                "/api/v1/spaces/" + ownerSpaceId + "/repositories")) {
            HttpResponse<String> inaccessibleResource = otherClient.send(
                    HttpRequest.newBuilder(URI.create(url(privatePath))).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(inaccessibleResource.statusCode()).isEqualTo(404);
        }

        HttpResponse<String> inaccessibleSearch = otherClient.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/search?q=Owner-only"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(inaccessibleSearch.statusCode()).isEqualTo(200);
        assertThat(inaccessibleSearch.body()).doesNotContain(privateTitle);

        HttpResponse<String> invite = client.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/workspaces/" + ownerWorkspaceId + "/members")))
                .header("Content-Type", "application/json")
                .header("X-Odoc-Csrf", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + otherEmail + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(invite.statusCode()).isEqualTo(201);
        UUID invitedMembershipId = UUID.fromString(invite.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));

        HttpResponse<String> visibleMembers = client.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/workspaces/" + ownerWorkspaceId + "/members")))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(visibleMembers.statusCode()).isEqualTo(200);
        assertThat(visibleMembers.body()).contains(email, otherEmail);

        HttpResponse<String> sharedPages = otherClient.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/spaces/" + ownerSpaceId + "/pages"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(sharedPages.statusCode()).isEqualTo(200);
        assertThat(sharedPages.body()).contains(privateTitle);

        HttpResponse<String> remove = client.send(HttpRequest.newBuilder(URI.create(
                        url("/api/v1/workspaces/" + ownerWorkspaceId + "/members/" + invitedMembershipId)))
                .header("X-Odoc-Csrf", csrf)
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(remove.statusCode()).isEqualTo(204);

        HttpResponse<String> removedMemberPages = otherClient.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/spaces/" + ownerSpaceId + "/pages"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(removedMemberPages.statusCode()).isEqualTo(404);

        HttpResponse<String> logout = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/logout")))
                .header("X-Odoc-Csrf", csrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> missingSession = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/auth/session"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(missingSession.statusCode()).isEqualTo(401);

        HttpResponse<String> invalidLogin = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(invalidLogin.statusCode()).isEqualTo(401);
        assertThat(invalidLogin.body()).doesNotContain(email);
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    private static String setting(Connection connection, String name) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SHOW " + name)) {
            result.next();
            return result.getString(1);
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int updateNameWhenRevisionMatches(Connection connection, UUID spaceId, String name, long revision)
            throws Exception {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE spaces SET name = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND revision = ?")) {
            update.setString(1, name);
            update.setObject(2, spaceId);
            update.setLong(3, revision);
            return update.executeUpdate();
        }
    }

    /**
     * The integration server binds to a random local port, while the reviewed contract advertises
     * the stable local-development endpoint. No other generated field is normalized.
     */
    private String normalizeEphemeralServerPort(String openApi) {
        return openApi.replaceAll("\\\"url\\\":\\\"http://localhost:\\d+\\\"", "\\\"url\\\":\\\"http://localhost:8080\\\"");
    }
}
