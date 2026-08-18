package com.strangequark.odoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PublicGithubRepositoryClientTest {
    @Test
    void readsRepositoryMetadataAndReadmeFromTheConfiguredEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(200, """
                    {"owner":{"login":"octo"},"name":"hello","html_url":"https://github.com/octo/hello",
                     "description":"Example","default_branch":"main","stargazers_count":42}
                    """));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("# Hello"));
            server.start();

            PublicGithubRepositoryClient client = new PublicGithubRepositoryClient(
                    new GithubClientProperties(server.url("/").uri()));
            GithubFetchedRepository repository = client.fetchPublicRepository("octo", "hello");

            assertThat(repository.owner()).isEqualTo("octo");
            assertThat(repository.readmeContent()).isEqualTo("# Hello");
            assertThat(server.takeRequest().getPath()).isEqualTo("/octo/hello");
            assertThat(server.takeRequest().getPath()).isEqualTo("/octo/hello/readme");
        }
    }

    @Test
    void mapsAnUpstreamFaultToASafeGatewayProblem() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503).setBody("provider internals"));
            server.start();

            PublicGithubRepositoryClient client = new PublicGithubRepositoryClient(
                    new GithubClientProperties(server.url("/").uri()));

            assertThatThrownBy(() -> client.fetchPublicRepository("octo", "hello"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> {
                        ResponseStatusException response = (ResponseStatusException) error;
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        assertThat(response.getReason()).isEqualTo("GitHub is unavailable. Try again shortly.");
                    });
        }
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
