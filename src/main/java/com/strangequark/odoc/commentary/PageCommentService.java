package com.strangequark.odoc.commentary;

import com.strangequark.odoc.page.PageAccessService;
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
    private final PageCommentRepository comments; private final PageAccessService pages; private final Clock clock;
    @Autowired
    PageCommentService(PageCommentRepository comments, PageAccessService pages) { this(comments, pages, Clock.systemUTC()); }
    PageCommentService(PageCommentRepository comments, PageAccessService pages, Clock clock) { this.comments = comments; this.pages = pages; this.clock = clock; }
    @Transactional(readOnly = true) List<PageCommentResponse> list(UUID pageId) {
        requirePage(pageId); return comments.findAllByPageIdOrderByCreatedAtAsc(pageId).stream().map(PageCommentResponse::from).toList();
    }
    @Transactional PageCommentResponse create(UUID pageId, CreatePageCommentRequest request, String author) {
        requirePage(pageId); UUID parentId = request.parentId();
        if (parentId != null && comments.findByIdAndPageId(parentId, pageId).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply must belong to this page.");
        return PageCommentResponse.from(comments.save(new PageComment(UUID.randomUUID(), pageId, parentId, author, request.body().trim(), clock.instant())));
    }
    private void requirePage(UUID pageId) { pages.requireAccessiblePage(pageId); }
}
