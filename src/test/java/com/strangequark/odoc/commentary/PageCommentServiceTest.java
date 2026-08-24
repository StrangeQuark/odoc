package com.strangequark.odoc.commentary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.page.PageAccessService;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageCommentServiceTest {
    @Mock private PageCommentRepository comments;
    @Mock private PageAccessService pages;
    @Mock private WorkspaceAccessService workspaces;

    @Test
    void createsCommentsWithTheAuthenticatedAuthorAndCommentPermission() {
        UUID pageId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(pages.requirePageAction(pageId, AuthorizationAction.PAGE_COMMENT)).thenReturn(spaceId);
        when(comments.save(any(PageComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PageCommentResponse response = service().create(pageId,
                new CreatePageCommentRequest("  Useful review note.  ", null), authorId, "member@example.test");

        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.author()).isEqualTo("member@example.test");
        assertThat(response.body()).isEqualTo("Useful review note.");
        verify(pages).requirePageAction(pageId, AuthorizationAction.PAGE_COMMENT);
    }

    @Test
    void letsAnAuthorDeleteTheirOwnCommentWithoutOwnerModeration() {
        UUID pageId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(pages.requirePageAction(pageId, AuthorizationAction.PAGE_COMMENT)).thenReturn(UUID.randomUUID());
        when(comments.findByIdAndPageId(commentId, pageId)).thenReturn(Optional.of(comment(commentId, pageId, currentUserId)));

        service().delete(pageId, commentId, currentUserId);

        verify(comments).delete(any(PageComment.class));
        verify(workspaces, never()).requireSpaceAction(any(), eq(AuthorizationAction.PAGE_COMMENT_MODERATE));
    }

    @Test
    void requiresOwnerModerationBeforeDeletingAnotherUsersComment() {
        UUID pageId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(pages.requirePageAction(pageId, AuthorizationAction.PAGE_COMMENT)).thenReturn(spaceId);
        when(comments.findByIdAndPageId(commentId, pageId))
                .thenReturn(Optional.of(comment(commentId, pageId, UUID.randomUUID())));

        service().delete(pageId, commentId, UUID.randomUUID());

        verify(workspaces).requireSpaceAction(spaceId, AuthorizationAction.PAGE_COMMENT_MODERATE);
        verify(comments).delete(any(PageComment.class));
    }

    private PageCommentService service() {
        return new PageCommentService(comments, pages, workspaces,
                Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC));
    }

    private static PageComment comment(UUID id, UUID pageId, UUID authorId) {
        return new PageComment(id, pageId, null, authorId, "author@example.test", "A note.", Instant.EPOCH);
    }
}
