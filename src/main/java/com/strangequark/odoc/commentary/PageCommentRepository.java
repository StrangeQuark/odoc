package com.strangequark.odoc.commentary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PageCommentRepository extends JpaRepository<PageComment, UUID> {
    List<PageComment> findAllByPageIdOrderByCreatedAtAsc(UUID pageId);
    Optional<PageComment> findByIdAndPageId(UUID id, UUID pageId);
}
