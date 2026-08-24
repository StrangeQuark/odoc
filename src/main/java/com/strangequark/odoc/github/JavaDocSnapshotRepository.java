package com.strangequark.odoc.github;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JavaDocSnapshotRepository extends JpaRepository<JavaDocSnapshot, UUID> {
    List<JavaDocSnapshot> findAllByRepositoryBindingIdOrderByTypeNameAsc(UUID repositoryBindingId);
    Optional<JavaDocSnapshot> findByRepositoryBindingIdAndSourcePath(UUID repositoryBindingId, String sourcePath);
}
