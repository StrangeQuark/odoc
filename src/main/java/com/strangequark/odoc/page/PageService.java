package com.strangequark.odoc.page;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.api.OptimisticConcurrency;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        requireSpace(spaceId, AuthorizationAction.PAGE_VIEW);
        return pages.findAllBySpaceIdAndArchivedAtIsNullOrderByUpdatedAtDesc(spaceId).stream().map(PageResponse::from).toList();
    }

    @Transactional(readOnly = true)
    List<PageTreeNode> tree(UUID spaceId) {
        requireSpace(spaceId, AuthorizationAction.PAGE_VIEW);
        List<Page> activePages = pages.findTop500BySpaceIdAndArchivedAtIsNullOrderByUpdatedAtDesc(spaceId);
        Map<UUID, List<Page>> children = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        for (Page page : activePages) ids.add(page.getId());
        for (Page page : activePages) {
            UUID parent = ids.contains(page.getParentId()) ? page.getParentId() : null;
            children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(page);
        }
        children.values().forEach(nodes -> nodes.sort(Comparator.comparing(Page::getTitle, String.CASE_INSENSITIVE_ORDER)));
        return treeFor(null, children, new HashSet<>());
    }

    @Transactional(readOnly = true)
    PageResponse get(UUID pageId) { return PageResponse.from(requirePage(pageId, AuthorizationAction.PAGE_VIEW)); }

    @Transactional
    PageResponse create(UUID spaceId, CreatePageRequest request) {
        requireSpace(spaceId, AuthorizationAction.PAGE_CREATE);
        UUID parentId = requireParentInSpace(spaceId, request.parentId());
        Instant now = clock.instant();
        String content = normalizedContent(request.content());
        Page page = new Page(UUID.randomUUID(), spaceId, parentId, request.title().trim(), content,
                PageContentText.from(content), workspaceAccess.currentUserId(), now);
        Page saved = pages.save(page);
        snapshot(saved, now);
        return PageResponse.from(saved);
    }

    @Transactional
    PageResponse update(UUID pageId, String ifMatch, UpdatePageRequest request) {
        Page page = requirePage(pageId, AuthorizationAction.PAGE_EDIT);
        if (page.isArchived()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
        OptimisticConcurrency.requireMatching(ifMatch, page.getRevision());
        Instant now = clock.instant();
        String content = normalizedContent(request.content());
        page.update(request.title().trim(), content, PageContentText.from(content), now);
        snapshot(page, now);
        return PageResponse.from(pages.saveAndFlush(page));
    }

    @Transactional
    void archive(UUID pageId) {
        Page page = requirePage(pageId, AuthorizationAction.PAGE_ARCHIVE);
        if (!page.isArchived()) page.archive(clock.instant());
    }

    @Transactional
    PageResponse move(UUID pageId, MovePageRequest request) {
        Page page = requirePage(pageId, AuthorizationAction.PAGE_MOVE);
        if (page.isArchived()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
        UUID parentId = requireParentInSpace(page.getSpaceId(), request.parentId());
        if (pageId.equals(parentId) || wouldCreateCycle(pageId, parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A page cannot be moved under itself or a descendant.");
        }
        page.moveTo(parentId, clock.instant());
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    List<PageResponse> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        // This is a simple documentation search field, not a query language.
        // Treat punctuation as word separators so titles such as "release-notes"
        // behave like a person expects instead of giving PostgreSQL websearch
        // syntax (where '-' means exclusion) surprising control over results.
        String term = query.trim().replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (term.isBlank()) return List.of();
        List<UUID> workspaceIds = workspaceAccess.workspaceIdsForCurrentUser();
        if (workspaceIds.isEmpty()) return List.of();
        return pages.searchInWorkspaces(workspaceIds, term).stream().map(PageResponse::from).toList();
    }

    @Transactional(readOnly = true)
    List<PageVersionResponse> history(UUID pageId) {
        requirePage(pageId, AuthorizationAction.PAGE_VIEW);
        return versions.findAllByPageIdOrderByVersionNumberDesc(pageId).stream().map(PageVersionResponse::from).toList();
    }

    @Transactional
    PageResponse restore(UUID pageId, UUID versionId) {
        Page page = requirePage(pageId, AuthorizationAction.PAGE_EDIT);
        PageVersion version = versions.findById(versionId)
                .filter(candidate -> candidate.getPageId().equals(pageId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page version not found."));
        Instant now = clock.instant();
        page.update(version.getTitle(), version.getContent(), PageContentText.from(version.getContent()), now);
        snapshot(page, now);
        return PageResponse.from(page);
    }

    private void snapshot(Page page, Instant now) {
        int nextVersion = versions.findTopByPageIdOrderByVersionNumberDesc(page.getId())
                .map(version -> version.getVersionNumber() + 1).orElse(1);
        versions.save(new PageVersion(UUID.randomUUID(), page.getId(), nextVersion, page.getTitle(), page.getContent(), now));
    }

    private void requireSpace(UUID spaceId, AuthorizationAction action) {
        workspaceAccess.requireSpaceAction(spaceId, action);
    }

    private Page requirePage(UUID pageId, AuthorizationAction action) {
        Page page = pages.findById(pageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found."));
        workspaceAccess.requireSpaceAction(page.getSpaceId(), action);
        return page;
    }

    private UUID requireParentInSpace(UUID spaceId, UUID parentId) {
        if (parentId == null) return null;
        Page parent = requirePage(parentId, AuthorizationAction.PAGE_VIEW);
        if (!parent.getSpaceId().equals(spaceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent page must belong to the same space.");
        }
        if (parent.isArchived()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An archived page cannot be a parent.");
        }
        return parentId;
    }

    private List<PageTreeNode> treeFor(UUID parentId, Map<UUID, List<Page>> children, Set<UUID> ancestors) {
        List<PageTreeNode> result = new ArrayList<>();
        for (Page page : children.getOrDefault(parentId, List.of())) {
            if (!ancestors.add(page.getId())) continue;
            result.add(new PageTreeNode(page.getId(), page.getParentId(), page.getTitle(), page.getRevision(),
                    page.getUpdatedAt(), treeFor(page.getId(), children, new HashSet<>(ancestors))));
            ancestors.remove(page.getId());
        }
        return result;
    }

    private boolean wouldCreateCycle(UUID pageId, UUID parentId) {
        UUID cursor = parentId;
        Set<UUID> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (pageId.equals(cursor)) return true;
            cursor = pages.findById(cursor).map(Page::getParentId).orElse(null);
        }
        return cursor != null;
    }

    private String normalizedContent(String content) { return content == null ? "" : content; }
}
