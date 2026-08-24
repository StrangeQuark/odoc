package com.strangequark.odoc.commentary;

import com.strangequark.odoc.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/pages/{pageId}/comments")
public class PageCommentController {
    private final PageCommentService comments;
    private final CurrentUser currentUser;
    PageCommentController(PageCommentService comments, CurrentUser currentUser) {
        this.comments = comments;
        this.currentUser = currentUser;
    }
    @GetMapping List<PageCommentResponse> list(@PathVariable UUID pageId) { return comments.list(pageId); }
    @PostMapping PageCommentResponse create(@PathVariable UUID pageId, @Valid @RequestBody CreatePageCommentRequest request) {
        var user = currentUser.require();
        return comments.create(pageId, request, user.id(), user.email());
    }
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID pageId, @PathVariable UUID commentId) {
        comments.delete(pageId, commentId, currentUser.requireId());
    }
}
