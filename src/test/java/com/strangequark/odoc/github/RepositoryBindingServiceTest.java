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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepositoryBindingServiceTest {
    @Mock private RepositoryBindingRepository bindings;
    @Mock private WorkspaceAccessService workspaceAccess;
    @Mock private GithubRepositoryClient github;

    @Test
    void attachesCanonicalGithubMetadataAndReadmeToASpace() {
        UUID spaceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        RepositoryBindingService service = new RepositoryBindingService(
                bindings, workspaceAccess, github, Clock.fixed(now, ZoneOffset.UTC));
        GithubFetchedRepository fetched = new GithubFetchedRepository(
                "spring-projects", "spring-boot", "https://github.com/spring-projects/spring-boot",
                "Spring Boot", "main", 77_000, "# Spring Boot", "README.adoc");
        when(bindings.existsBySpaceIdAndGithubUrl(spaceId, fetched.canonicalUrl())).thenReturn(false);
        when(github.fetchPublicRepository("spring-projects", "spring-boot")).thenReturn(fetched);
        when(bindings.save(any(RepositoryBinding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RepositoryBindingResponse response = service.attach(
                spaceId, new AttachGithubRepositoryRequest("https://github.com/spring-projects/spring-boot/"));

        assertThat(response.githubUrl()).isEqualTo(fetched.canonicalUrl());
        assertThat(response.readmeContent()).isEqualTo("# Spring Boot");
        assertThat(response.syncedAt()).isEqualTo(now);
        verify(workspaceAccess).requireSpaceAction(spaceId, AuthorizationAction.REPOSITORY_CONNECT);
    }

    @Test
    void rejectsNonCanonicalGithubUrlsBeforeFetching() {
        RepositoryBindingService service = new RepositoryBindingService(bindings, workspaceAccess, github);

        assertThatThrownBy(() -> service.attach(UUID.randomUUID(),
                new AttachGithubRepositoryRequest("https://github.com/octo/hello?ref=main")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("HTTPS public GitHub repository URL");
    }

    @Test
    void refreshesAnExistingRepositoryInItsSpace() {
        UUID spaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        RepositoryBindingService service = new RepositoryBindingService(
                bindings, workspaceAccess, github, Clock.fixed(now, ZoneOffset.UTC));
        GithubFetchedRepository before = new GithubFetchedRepository(
                "octo", "hello", "https://github.com/octo/hello", "Before", "main", 1, "# Before", "README.md");
        GithubFetchedRepository refreshed = new GithubFetchedRepository(
                "octo", "hello", "https://github.com/octo/hello", "After", "main", 2, "# After", "README.md");
        RepositoryBinding binding = new RepositoryBinding(repositoryId, spaceId, before, Instant.EPOCH);
        when(bindings.findByIdAndSpaceId(repositoryId, spaceId)).thenReturn(java.util.Optional.of(binding));
        when(github.fetchPublicRepository("octo", "hello")).thenReturn(refreshed);
        when(bindings.saveAndFlush(binding)).thenReturn(binding);

        RepositoryBindingResponse response = service.refresh(spaceId, repositoryId);

        assertThat(response.description()).isEqualTo("After");
        assertThat(response.readmeContent()).isEqualTo("# After");
        assertThat(response.syncedAt()).isEqualTo(now);
        verify(workspaceAccess).requireSpaceAction(spaceId, AuthorizationAction.REPOSITORY_CONNECT);
    }
}
