package com.strangequark.odoc.github;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
class PublicGithubRepositoryClient implements GithubRepositoryClient {
    private static final String GITHUB_API = "https://api.github.com/repos/{owner}/{repository}";
    private final RestClient client;

    PublicGithubRepositoryClient() {
        this.client = RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT, "odoc-local-mvp").build();
    }

    @Override
    public GithubFetchedRepository fetchPublicRepository(String owner, String repository) {
        try {
            JsonNode details = client.get().uri(GITHUB_API, owner, repository)
                    .retrieve().body(JsonNode.class);
            if (details == null) throw unavailable();
            String readme = fetchReadme(owner, repository);
            return new GithubFetchedRepository(
                    details.path("owner").path("login").asText(owner),
                    details.path("name").asText(repository),
                    details.path("html_url").asText("https://github.com/" + owner + "/" + repository),
                    details.path("description").asText(""),
                    details.path("default_branch").asText(""),
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
            String readme = client.get().uri(URI.create("https://api.github.com/repos/" + owner + "/" + repository + "/readme"))
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
}
