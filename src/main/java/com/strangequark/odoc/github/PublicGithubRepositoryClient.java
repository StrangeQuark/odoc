package com.strangequark.odoc.github;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
class PublicGithubRepositoryClient implements GithubRepositoryClient {
    private static final int MAX_README_CHARACTERS = 500_000;
    private final RestClient client;

    PublicGithubRepositoryClient(GithubClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .baseUrl(properties.apiBaseUrl().toString())
                .defaultHeader(HttpHeaders.USER_AGENT, "odoc-local-mvp")
                .requestFactory(requestFactory)
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
            if (rateLimited(exception)) throw rateLimited();
            throw unavailable(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public String fetchJavaSource(String owner, String repository, String sourcePath) {
        try {
            // sourcePath is validated by JavaDocService and must retain `/`
            // separators for GitHub's nested contents endpoint.
            String source = client.get().uri("/{owner}/{repository}/contents/" + sourcePath, owner, repository)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json")
                    .retrieve().body(String.class);
            if (source == null || source.length() > 500_000) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "That Java source file is empty or too large for Odoc to parse.");
            }
            return source;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Java source file not found in this public repository.", exception);
            }
            if (rateLimited(exception)) throw rateLimited();
            throw unavailable(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private String fetchReadme(String owner, String repository) {
        try {
            String readme = client.get().uri("/{owner}/{repository}/readme", owner, repository)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json")
                    .retrieve().body(String.class);
            if (readme == null) return "";
            return readme.length() <= MAX_README_CHARACTERS
                    ? readme
                    : readme.substring(0, MAX_README_CHARACTERS) + "\n\n[README truncated by Odoc]";
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) return "";
            if (rateLimited(exception)) throw rateLimited();
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub is unavailable. Try again shortly.");
    }

    private ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub is unavailable. Try again shortly.", cause);
    }

    private boolean rateLimited(RestClientResponseException exception) {
        return exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                || (exception.getStatusCode() == HttpStatus.FORBIDDEN
                        && "0".equals(exception.getResponseHeaders().getFirst("X-RateLimit-Remaining")));
    }

    private ResponseStatusException rateLimited() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "GitHub is rate-limiting requests. Try again shortly.");
    }

    private String textOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        String value = node.stringValue();
        return value == null || value.isBlank() ? fallback : value;
    }
}
