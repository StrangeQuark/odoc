package com.strangequark.odoc.commentary;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pages/{pageId}/comments")
public class PageCommentController {
    private final PageCommentService comments;
    PageCommentController(PageCommentService comments) { this.comments = comments; }
    @GetMapping List<PageCommentResponse> list(@PathVariable UUID pageId) { return comments.list(pageId); }
    @PostMapping PageCommentResponse create(@PathVariable UUID pageId, @Valid @RequestBody CreatePageCommentRequest request, Principal principal) { return comments.create(pageId, request, principal.getName()); }
}
