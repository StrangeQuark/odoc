package com.strangequark.odoc.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repository_bindings")
class RepositoryBinding {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID spaceId;
    @Column(nullable = false)
    private String githubUrl;
    @Column(nullable = false)
    private String owner;
    @Column(name = "repository_name", nullable = false)
    private String repositoryName;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private String defaultBranch;
    @Column(nullable = false)
    private int stars;
    @Column(nullable = false)
    private String readmeContent;
    @Column(nullable = false)
    private String readmePath;
    @Column(nullable = false)
    private Instant syncedAt;

    protected RepositoryBinding() {}

    RepositoryBinding(UUID id, UUID spaceId, GithubFetchedRepository fetched, Instant syncedAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.githubUrl = fetched.canonicalUrl();
        this.owner = fetched.owner();
        this.repositoryName = fetched.name();
        this.description = fetched.description();
        this.defaultBranch = fetched.defaultBranch();
        this.stars = fetched.stars();
        this.readmeContent = fetched.readmeContent();
        this.readmePath = fetched.readmePath();
        this.syncedAt = syncedAt;
    }

    UUID id() { return id; }
    UUID spaceId() { return spaceId; }
    String githubUrl() { return githubUrl; }
    String owner() { return owner; }
    String repositoryName() { return repositoryName; }
    String description() { return description; }
    String defaultBranch() { return defaultBranch; }
    int stars() { return stars; }
    String readmeContent() { return readmeContent; }
    String readmePath() { return readmePath; }
    Instant syncedAt() { return syncedAt; }
}
