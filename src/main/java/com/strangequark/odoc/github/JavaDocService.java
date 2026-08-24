package com.strangequark.odoc.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Stores only parsed documentation from a chosen public Java source file. */
@Service
class JavaDocService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<JavaDocMember>> MEMBERS = new TypeReference<>() {};
    private final JavaDocSnapshotRepository snapshots;
    private final RepositoryBindingRepository bindings;
    private final WorkspaceAccessService workspaceAccess;
    private final GithubRepositoryClient github;
    private final Clock clock;

    @Autowired
    JavaDocService(JavaDocSnapshotRepository snapshots, RepositoryBindingRepository bindings,
                   WorkspaceAccessService workspaceAccess, GithubRepositoryClient github) {
        this(snapshots, bindings, workspaceAccess, github, Clock.systemUTC());
    }

    JavaDocService(JavaDocSnapshotRepository snapshots, RepositoryBindingRepository bindings,
                   WorkspaceAccessService workspaceAccess, GithubRepositoryClient github, Clock clock) {
        this.snapshots = snapshots;
        this.bindings = bindings;
        this.workspaceAccess = workspaceAccess;
        this.github = github;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<JavaDocSnapshotResponse> list(UUID spaceId, UUID repositoryId) {
        workspaceAccess.requireSpaceAction(spaceId, AuthorizationAction.REPOSITORY_VIEW);
        requireBinding(spaceId, repositoryId);
        return snapshots.findAllByRepositoryBindingIdOrderByTypeNameAsc(repositoryId).stream()
                .map(this::response).toList();
    }

    @Transactional
    JavaDocSnapshotResponse refresh(UUID spaceId, UUID repositoryId, RefreshJavaDocRequest request) {
        workspaceAccess.requireSpaceAction(spaceId, AuthorizationAction.REPOSITORY_CONNECT);
        String path = sourcePath(request.sourcePath());
        RepositoryBinding binding = requireBinding(spaceId, repositoryId);
        String source = github.fetchJavaSource(binding.owner(), binding.repositoryName(), path);
        ParsedJavaDoc parsed = JavaDocParser.parse(source);
        String membersJson;
        try {
            membersJson = JSON.writeValueAsString(parsed.members());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not store parsed Java documentation.", exception);
        }
        JavaDocSnapshot snapshot = snapshots.findByRepositoryBindingIdAndSourcePath(repositoryId, path)
                .orElseGet(() -> new JavaDocSnapshot(UUID.randomUUID(), repositoryId, path, parsed, membersJson, clock.instant()));
        snapshot.update(parsed, membersJson, clock.instant());
        return response(snapshots.saveAndFlush(snapshot));
    }

    private RepositoryBinding requireBinding(UUID spaceId, UUID repositoryId) {
        return bindings.findByIdAndSpaceId(repositoryId, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found."));
    }

    private String sourcePath(String path) {
        if (path == null || path.isBlank() || path.length() > 500
                || path.startsWith("/") || path.contains("..")
                || !path.matches("[A-Za-z0-9_./-]+") || !path.endsWith(".java")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use a relative .java source path such as src/main/java/example/Guide.java.");
        }
        return path;
    }

    private JavaDocSnapshotResponse response(JavaDocSnapshot snapshot) {
        try {
            return new JavaDocSnapshotResponse(snapshot.id(), snapshot.sourcePath(), snapshot.packageName(),
                    snapshot.typeName(), snapshot.typeKind(), snapshot.documentation(),
                    JSON.readValue(snapshot.membersJson(), MEMBERS), snapshot.refreshedAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored Java documentation is invalid.", exception);
        }
    }
}
