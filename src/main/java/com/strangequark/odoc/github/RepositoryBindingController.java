package com.strangequark.odoc.github;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/repositories")
class RepositoryBindingController {
    private final RepositoryBindingService bindings;
    private final JavaDocService javaDocs;

    RepositoryBindingController(RepositoryBindingService bindings, JavaDocService javaDocs) {
        this.bindings = bindings;
        this.javaDocs = javaDocs;
    }

    @GetMapping
    List<RepositoryBindingResponse> list(@PathVariable UUID spaceId) { return bindings.list(spaceId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RepositoryBindingResponse attach(@PathVariable UUID spaceId, @Valid @RequestBody AttachGithubRepositoryRequest request) {
        return bindings.attach(spaceId, request);
    }

    @PostMapping("/{repositoryId}/refresh")
    RepositoryBindingResponse refresh(@PathVariable UUID spaceId, @PathVariable UUID repositoryId) {
        return bindings.refresh(spaceId, repositoryId);
    }

    @GetMapping("/{repositoryId}/javadocs")
    List<JavaDocSnapshotResponse> javaDocs(@PathVariable UUID spaceId, @PathVariable UUID repositoryId) {
        return javaDocs.list(spaceId, repositoryId);
    }

    @PostMapping("/{repositoryId}/javadocs")
    JavaDocSnapshotResponse refreshJavaDocs(
            @PathVariable UUID spaceId, @PathVariable UUID repositoryId,
            @Valid @RequestBody RefreshJavaDocRequest request) {
        return javaDocs.refresh(spaceId, repositoryId, request);
    }
}
