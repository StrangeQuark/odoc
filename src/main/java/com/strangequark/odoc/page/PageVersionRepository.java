package com.strangequark.odoc.page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PageVersionRepository extends JpaRepository<PageVersion, UUID> {
    List<PageVersion> findAllByPageIdOrderByVersionNumberDesc(UUID pageId);
    Optional<PageVersion> findTopByPageIdOrderByVersionNumberDesc(UUID pageId);
}
