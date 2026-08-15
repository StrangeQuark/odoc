package com.strangequark.odoc.github;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RepositoryBindingRepository extends JpaRepository<RepositoryBinding, UUID> {
    List<RepositoryBinding> findAllBySpaceIdOrderByRepositoryNameAsc(UUID spaceId);
    boolean existsBySpaceIdAndGithubUrl(UUID spaceId, String githubUrl);
}
