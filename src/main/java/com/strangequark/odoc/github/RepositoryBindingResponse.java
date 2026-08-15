package com.strangequark.odoc.github;

import java.time.Instant;
import java.util.UUID;

record RepositoryBindingResponse(
        UUID id, UUID spaceId, String githubUrl, String owner, String name, String description,
        String defaultBranch, int stars, String readmeContent, String readmePath, Instant syncedAt) {
    static RepositoryBindingResponse from(RepositoryBinding binding) {
        return new RepositoryBindingResponse(binding.id(), binding.spaceId(), binding.githubUrl(), binding.owner(),
                binding.repositoryName(), binding.description(), binding.defaultBranch(), binding.stars(),
                binding.readmeContent(), binding.readmePath(), binding.syncedAt());
    }
}
