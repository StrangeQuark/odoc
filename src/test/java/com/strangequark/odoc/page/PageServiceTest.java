package com.strangequark.odoc.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.space.SpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageServiceTest {
    @Mock private PageRepository pages;
    @Mock private PageVersionRepository versions;
    @Mock private SpaceRepository spaces;

    @Test
    void delegatesTrimmedQueriesToThePostgresFullTextSearch() {
        PageService service = new PageService(pages, versions, spaces);
        when(pages.search("deployment guide")).thenReturn(List.of());

        assertThat(service.search("  deployment guide  ")).isEmpty();

        verify(pages).search("deployment guide");
    }

    @Test
    void doesNotSearchForBlankQueries() {
        PageService service = new PageService(pages, versions, spaces);

        assertThat(service.search("  ")).isEmpty();
    }

    @Test
    void snapshotsEachSaveAsTheNextVersion() {
        UUID spaceId = UUID.randomUUID();
        PageService service = new PageService(pages, versions, spaces,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        when(spaces.existsById(spaceId)).thenReturn(true);
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
        PageService service = new PageService(pages, versions, spaces);
        when(spaces.existsById(spaceId)).thenReturn(true);
        when(pages.findById(parentId)).thenReturn(Optional.of(parent));
        when(pages.save(any(Page.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findTopByPageIdOrderByVersionNumberDesc(any())).thenReturn(Optional.empty());

        service.create(spaceId, new CreatePageRequest("Child", "", parentId));

        ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);
        verify(pages).save(page.capture());
        assertThat(page.getValue().getParentId()).isEqualTo(parentId);
    }
}
