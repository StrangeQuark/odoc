package com.strangequark.odoc.page;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PageService {
    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final WorkspaceAccessService workspaceAccess;
    private final Clock clock;

    @Autowired
    PageService(PageRepository pages, PageVersionRepository versions, WorkspaceAccessService workspaceAccess) {
        this(pages, versions, workspaceAccess, Clock.systemUTC());
    }
    PageService(PageRepository pages, PageVersionRepository versions, WorkspaceAccessService workspaceAccess, Clock clock) {
        this.pages = pages;
        this.versions = versions;
        this.workspaceAccess = workspaceAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<PageResponse> list(UUID spaceId) {
        requireSpace(spaceId);
        return pages.findAllBySpaceIdOrderByUpdatedAtDesc(spaceId).stream().map(PageResponse::from).toList();
    }

    @Transactional(readOnly = true)
    PageResponse get(UUID pageId) { return PageResponse.from(requirePage(pageId)); }

    @Transactional
    PageResponse create(UUID spaceId, CreatePageRequest request) {
        requireSpace(spaceId);
        UUID parentId = requireParentInSpace(spaceId, request.parentId());
        Instant now = clock.instant();
        Page page = new Page(UUID.randomUUID(), spaceId, parentId, request.title().trim(), normalizedContent(request.content()), now);
        Page saved = pages.save(page);
        snapshot(saved, now);
        return PageResponse.from(saved);
    }

    @Transactional
    PageResponse update(UUID pageId, UpdatePageRequest request) {
        Page page = requirePage(pageId);
        Instant now = clock.instant();
        page.update(request.title().trim(), normalizedContent(request.content()), now);
        snapshot(page, now);
        return PageResponse.from(page);
    }

    @Transactional
    void delete(UUID pageId) { pages.delete(requirePage(pageId)); }

    @Transactional(readOnly = true)
    List<PageResponse> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<UUID> workspaceIds = workspaceAccess.workspaceIdsForCurrentUser();
        if (workspaceIds.isEmpty()) return List.of();
        String term = query.trim();
        return pages.searchInWorkspaces(workspaceIds, term).stream().map(PageResponse::from).toList();
    }

    @Transactional(readOnly = true)
    List<PageVersionResponse> history(UUID pageId) {
        requirePage(pageId);
        return versions.findAllByPageIdOrderByVersionNumberDesc(pageId).stream().map(PageVersionResponse::from).toList();
    }

    @Transactional
    PageResponse restore(UUID pageId, UUID versionId) {
        Page page = requirePage(pageId);
        PageVersion version = versions.findById(versionId)
                .filter(candidate -> candidate.getPageId().equals(pageId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page version not found."));
        Instant now = clock.instant();
        page.update(version.getTitle(), version.getContent(), now);
        snapshot(page, now);
        return PageResponse.from(page);
    }

    private void snapshot(Page page, Instant now) {
        int nextVersion = versions.findTopByPageIdOrderByVersionNumberDesc(page.getId())
                .map(version -> version.getVersionNumber() + 1).orElse(1);
        versions.save(new PageVersion(UUID.randomUUID(), page.getId(), nextVersion, page.getTitle(), page.getContent(), now));
    }

    private void requireSpace(UUID spaceId) {
        workspaceAccess.requireAccessibleSpace(spaceId);
    }

    private Page requirePage(UUID pageId) {
        Page page = pages.findById(pageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found."));
        workspaceAccess.requireAccessibleSpace(page.getSpaceId());
        return page;
    }

    private UUID requireParentInSpace(UUID spaceId, UUID parentId) {
        if (parentId == null) return null;
        Page parent = requirePage(parentId);
        if (!parent.getSpaceId().equals(spaceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent page must belong to the same space.");
        }
        return parentId;
    }

    private String normalizedContent(String content) { return content == null ? "" : content; }
}
