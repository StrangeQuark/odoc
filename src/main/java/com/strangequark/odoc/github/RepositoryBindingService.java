package com.strangequark.odoc.github;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import com.strangequark.odoc.authorization.AuthorizationAction;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class RepositoryBindingService {
    private final RepositoryBindingRepository bindings;
    private final WorkspaceAccessService workspaceAccess;
    private final GithubRepositoryClient github;
    private final Clock clock;

    @Autowired
    RepositoryBindingService(
            RepositoryBindingRepository bindings, WorkspaceAccessService workspaceAccess, GithubRepositoryClient github) {
        this(bindings, workspaceAccess, github, Clock.systemUTC());
    }

    RepositoryBindingService(
            RepositoryBindingRepository bindings, WorkspaceAccessService workspaceAccess, GithubRepositoryClient github, Clock clock) {
        this.bindings = bindings;
        this.workspaceAccess = workspaceAccess;
        this.github = github;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<RepositoryBindingResponse> list(UUID spaceId) {
        requireSpace(spaceId, AuthorizationAction.REPOSITORY_VIEW);
        return bindings.findAllBySpaceIdOrderByRepositoryNameAsc(spaceId).stream().map(RepositoryBindingResponse::from).toList();
    }

    @Transactional
    RepositoryBindingResponse attach(UUID spaceId, AttachGithubRepositoryRequest request) {
        requireSpace(spaceId, AuthorizationAction.REPOSITORY_CONNECT);
        GithubCoordinates coordinates = GithubCoordinates.parse(request.url());
        String canonicalUrl = "https://github.com/" + coordinates.owner() + "/" + coordinates.repository();
        if (bindings.existsBySpaceIdAndGithubUrl(spaceId, canonicalUrl)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This repository is already attached to the space.");
        }
        GithubFetchedRepository fetched = github.fetchPublicRepository(coordinates.owner(), coordinates.repository());
        RepositoryBinding binding = new RepositoryBinding(UUID.randomUUID(), spaceId, fetched, clock.instant());
        return RepositoryBindingResponse.from(bindings.save(binding));
    }

    private void requireSpace(UUID spaceId, AuthorizationAction action) {
        workspaceAccess.requireSpaceAction(spaceId, action);
    }

    private record GithubCoordinates(String owner, String repository) {
        static GithubCoordinates parse(String url) {
            URI uri = URI.create(url.trim());
            String[] parts = uri.getPath().replaceAll("^/+|/+$", "").split("/");
            if (!"github.com".equalsIgnoreCase(uri.getHost()) || parts.length != 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a canonical public GitHub repository URL.");
            }
            return new GithubCoordinates(parts[0], parts[1]);
        }
    }
}
