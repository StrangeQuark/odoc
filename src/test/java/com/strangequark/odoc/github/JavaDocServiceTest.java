package com.strangequark.odoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JavaDocServiceTest {
    @Mock private JavaDocSnapshotRepository snapshots;
    @Mock private RepositoryBindingRepository bindings;
    @Mock private WorkspaceAccessService workspaceAccess;
    @Mock private GithubRepositoryClient github;

    @Test
    void fetchesAndStoresOnlyParsedJavaDocumentationForASelectedSourcePath() {
        UUID spaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        JavaDocService service = new JavaDocService(snapshots, bindings, workspaceAccess, github,
                Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC));
        RepositoryBinding binding = new RepositoryBinding(repositoryId, spaceId,
                new GithubFetchedRepository("octo", "guide", "https://github.com/octo/guide", "", "main", 0, "", ""), Instant.EPOCH);
        when(bindings.findByIdAndSpaceId(repositoryId, spaceId)).thenReturn(Optional.of(binding));
        when(github.fetchJavaSource("octo", "guide", "src/main/java/example/Guide.java")).thenReturn("""
                package example;
                /** A guide. */ public class Guide {
                  /** Opens the guide. @return true when open */ public boolean open() { return true; }
                }
                """);
        when(snapshots.findByRepositoryBindingIdAndSourcePath(repositoryId, "src/main/java/example/Guide.java"))
                .thenReturn(Optional.empty());
        when(snapshots.saveAndFlush(any(JavaDocSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JavaDocSnapshotResponse response = service.refresh(spaceId, repositoryId,
                new RefreshJavaDocRequest("src/main/java/example/Guide.java"));

        assertThat(response.typeName()).isEqualTo("Guide");
        assertThat(response.members()).singleElement().extracting(JavaDocMember::name).isEqualTo("open");
        verify(workspaceAccess).requireSpaceAction(spaceId, AuthorizationAction.REPOSITORY_CONNECT);
        verify(github).fetchJavaSource("octo", "guide", "src/main/java/example/Guide.java");
    }

    @Test
    void refusesPathTraversalBeforeCallingGithub() {
        JavaDocService service = new JavaDocService(snapshots, bindings, workspaceAccess, github);

        assertThatThrownBy(() -> service.refresh(UUID.randomUUID(), UUID.randomUUID(),
                new RefreshJavaDocRequest("../pom.xml")))
                .hasMessageContaining("relative .java source path");
    }
}
