package com.strangequark.odoc.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageServiceTest {
    @Mock private PageRepository pages;
    @Mock private PageVersionRepository versions;
    @Mock private WorkspaceAccessService workspaceAccess;

    @Test
    void delegatesTrimmedQueriesToThePostgresFullTextSearch() {
        UUID workspaceId = UUID.randomUUID();
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(workspaceAccess.workspaceIdsForCurrentUser()).thenReturn(List.of(workspaceId));
        when(pages.searchInWorkspaces(List.of(workspaceId), "deployment guide")).thenReturn(List.of());

        assertThat(service.search("  deployment guide  ")).isEmpty();

        verify(pages).searchInWorkspaces(List.of(workspaceId), "deployment guide");
    }

    @Test
    void doesNotSearchForBlankQueries() {
        PageService service = new PageService(pages, versions, workspaceAccess);

        assertThat(service.search("  ")).isEmpty();
    }

    @Test
    void treatsPunctuationAsWordSeparatorsRatherThanPostgresQuerySyntax() {
        UUID workspaceId = UUID.randomUUID();
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(workspaceAccess.workspaceIdsForCurrentUser()).thenReturn(List.of(workspaceId));
        when(pages.searchInWorkspaces(List.of(workspaceId), "release notes")).thenReturn(List.of());

        assertThat(service.search("release-notes")).isEmpty();

        verify(pages).searchInWorkspaces(List.of(workspaceId), "release notes");
    }

    @Test
    void snapshotsEachSaveAsTheNextVersion() {
        UUID spaceId = UUID.randomUUID();
        PageService service = new PageService(pages, versions, workspaceAccess,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        when(pages.save(any(Page.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findTopByPageIdOrderByVersionNumberDesc(any())).thenReturn(Optional.empty());

        service.create(spaceId, new CreatePageRequest("Getting started", "First draft", null));

        verify(versions).save(org.mockito.ArgumentMatchers.argThat(version ->
                version.getVersionNumber() == 1 && version.getTitle().equals("Getting started")));
    }

    @Test
    void createsAChildPageOnlyUnderAParentInTheSameSpace() {
        UUID spaceId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Page parent = new Page(parentId, spaceId, null, "Parent", "", Instant.EPOCH);
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(pages.findById(parentId)).thenReturn(Optional.of(parent));
        when(pages.save(any(Page.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findTopByPageIdOrderByVersionNumberDesc(any())).thenReturn(Optional.empty());

        service.create(spaceId, new CreatePageRequest("Child", "", parentId));

        ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);
        verify(pages).save(page.capture());
        assertThat(page.getValue().getParentId()).isEqualTo(parentId);
    }

    @Test
    void extractsVisibleTextFromTiptapContentWhenCreatingAPage() {
        UUID spaceId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(workspaceAccess.currentUserId()).thenReturn(authorId);
        when(pages.save(any(Page.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findTopByPageIdOrderByVersionNumberDesc(any())).thenReturn(Optional.empty());

        service.create(spaceId, new CreatePageRequest("Guide", """
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Useful documentation"}]}]}
                """, null));

        ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);
        verify(pages).save(page.capture());
        assertThat(page.getValue().getPlainText()).isEqualTo("Useful documentation");
        assertThat(page.getValue().getAuthorId()).isEqualTo(authorId);
    }

    @Test
    void extractsVisibleTextFromTheVersionedRichDocumentEnvelope() {
        UUID spaceId = UUID.randomUUID();
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(pages.save(any(Page.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findTopByPageIdOrderByVersionNumberDesc(any())).thenReturn(Optional.empty());

        service.create(spaceId, new CreatePageRequest("Guide", """
                {"schemaVersion":1,"document":{"type":"doc","content":[{"type":"heading","content":[{"type":"text","text":"Searchable rich text"}]}]}}
                """, null));

        ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);
        verify(pages).save(page.capture());
        assertThat(page.getValue().getPlainText()).isEqualTo("Searchable rich text");
    }

    @Test
    void rejectsAStalePageRevisionBeforeChangingContent() {
        UUID pageId = UUID.randomUUID();
        Page page = new Page(pageId, UUID.randomUUID(), null, "Before", "", Instant.EPOCH);
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(pages.findById(pageId)).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> service.update(pageId, "\"revision-1\"", new UpdatePageRequest("After", "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Resource revision does not match");

        assertThat(page.getTitle()).isEqualTo("Before");
    }

    @Test
    void acceptsTheCurrentRevisionAndReturnsTheUpdatedPage() {
        UUID pageId = UUID.randomUUID();
        Page page = new Page(pageId, UUID.randomUUID(), null, "Before", "", Instant.EPOCH);
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(pages.findById(pageId)).thenReturn(Optional.of(page));
        when(pages.saveAndFlush(page)).thenReturn(page);
        when(versions.findTopByPageIdOrderByVersionNumberDesc(any())).thenReturn(Optional.empty());

        PageResponse response = service.update(pageId, "\"revision-0\"", new UpdatePageRequest("After", "New text"));

        assertThat(response.title()).isEqualTo("After");
        assertThat(response.plainText()).isEqualTo("New text");
        verify(pages).saveAndFlush(page);
    }

    @Test
    void returnsANestedTreeWithStableTitleOrdering() {
        UUID spaceId = UUID.randomUUID();
        Page parent = new Page(UUID.randomUUID(), spaceId, null, "Parent", "", Instant.EPOCH);
        Page child = new Page(UUID.randomUUID(), spaceId, parent.getId(), "Child", "", Instant.EPOCH);
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(pages.findTop500BySpaceIdAndArchivedAtIsNullOrderByUpdatedAtDesc(spaceId)).thenReturn(List.of(child, parent));

        List<PageTreeNode> tree = service.tree(spaceId);

        assertThat(tree).singleElement().satisfies(root -> {
            assertThat(root.id()).isEqualTo(parent.getId());
            assertThat(root.children()).extracting(PageTreeNode::id).containsExactly(child.getId());
        });
    }

    @Test
    void rejectsMovingAPageUnderItsDescendant() {
        UUID spaceId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Page parent = new Page(parentId, spaceId, null, "Parent", "", Instant.EPOCH);
        Page child = new Page(childId, spaceId, parentId, "Child", "", Instant.EPOCH);
        PageService service = new PageService(pages, versions, workspaceAccess);
        when(pages.findById(parentId)).thenReturn(Optional.of(parent));
        when(pages.findById(childId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.move(parentId, new MovePageRequest(childId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be moved under itself or a descendant");
    }

    @Test
    void archivesRatherThanHardDeletingAPage() {
        UUID pageId = UUID.randomUUID();
        Page page = new Page(pageId, UUID.randomUUID(), null, "Draft", "", Instant.EPOCH);
        PageService service = new PageService(pages, versions, workspaceAccess,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        when(pages.findById(pageId)).thenReturn(Optional.of(page));

        service.archive(pageId);

        assertThat(page.isArchived()).isTrue();
    }
}
