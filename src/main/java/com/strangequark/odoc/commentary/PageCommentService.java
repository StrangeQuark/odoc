package com.strangequark.odoc.commentary;

import com.strangequark.odoc.page.PageAccessService;
import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PageCommentService {
    private final PageCommentRepository comments;
    private final PageAccessService pages;
    private final WorkspaceAccessService workspaces;
    private final Clock clock;
    @Autowired
    PageCommentService(PageCommentRepository comments, PageAccessService pages, WorkspaceAccessService workspaces) {
        this(comments, pages, workspaces, Clock.systemUTC());
    }
    PageCommentService(PageCommentRepository comments, PageAccessService pages, WorkspaceAccessService workspaces, Clock clock) {
        this.comments = comments; this.pages = pages; this.workspaces = workspaces; this.clock = clock;
    }
    @Transactional(readOnly = true) List<PageCommentResponse> list(UUID pageId) {
        requirePage(pageId); return comments.findAllByPageIdOrderByCreatedAtAsc(pageId).stream().map(PageCommentResponse::from).toList();
    }
    @Transactional PageCommentResponse create(UUID pageId, CreatePageCommentRequest request, UUID authorId, String author) {
        pages.requirePageAction(pageId, AuthorizationAction.PAGE_COMMENT); UUID parentId = request.parentId();
        if (parentId != null && comments.findByIdAndPageId(parentId, pageId).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply must belong to this page.");
        return PageCommentResponse.from(comments.save(new PageComment(UUID.randomUUID(), pageId, parentId,
                authorId, author, request.body().trim(), clock.instant())));
    }
    @Transactional void delete(UUID pageId, UUID commentId, UUID currentUserId) {
        UUID spaceId = pages.requirePageAction(pageId, AuthorizationAction.PAGE_COMMENT);
        PageComment comment = comments.findByIdAndPageId(commentId, pageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
        if (!currentUserId.equals(comment.getAuthorId())) {
            workspaces.requireSpaceAction(spaceId, AuthorizationAction.PAGE_COMMENT_MODERATE);
        }
        comments.delete(comment);
    }
    private void requirePage(UUID pageId) { pages.requireAccessiblePage(pageId); }
}
