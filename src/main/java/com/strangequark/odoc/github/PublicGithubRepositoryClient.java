package com.strangequark.odoc.github;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
class PublicGithubRepositoryClient implements GithubRepositoryClient {
    private final RestClient client;

    PublicGithubRepositoryClient(GithubClientProperties properties) {
        this.client = RestClient.builder()
                .baseUrl(properties.apiBaseUrl().toString())
                .defaultHeader(HttpHeaders.USER_AGENT, "odoc-local-mvp")
                .build();
    }

    @Override
    public GithubFetchedRepository fetchPublicRepository(String owner, String repository) {
        try {
            JsonNode details = client.get().uri("/{owner}/{repository}", owner, repository)
                    .retrieve().body(JsonNode.class);
            if (details == null) throw unavailable();
            String readme = fetchReadme(owner, repository);
            return new GithubFetchedRepository(
                    textOrDefault(details.path("owner").path("login"), owner),
                    textOrDefault(details.path("name"), repository),
                    textOrDefault(details.path("html_url"), "https://github.com/" + owner + "/" + repository),
                    textOrDefault(details.path("description"), ""),
                    textOrDefault(details.path("default_branch"), ""),
                    details.path("stargazers_count").asInt(0),
                    readme,
                    "README");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Public GitHub repository not found.", exception);
            }
            throw unavailable(exception);
        }
    }

    private String fetchReadme(String owner, String repository) {
        try {
            String readme = client.get().uri("/{owner}/{repository}/readme", owner, repository)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json")
                    .retrieve().body(String.class);
            return readme == null ? "" : readme;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) return "";
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub is unavailable. Try again shortly.");
    }

    private ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub is unavailable. Try again shortly.", cause);
    }

    private String textOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        String value = node.stringValue();
        return value == null || value.isBlank() ? fallback : value;
    }
}
