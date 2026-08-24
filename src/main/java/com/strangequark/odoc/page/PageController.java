package com.strangequark.odoc.page;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PageController {
    private final PageService pages;

    PageController(PageService pages) { this.pages = pages; }

    @GetMapping("/spaces/{spaceId}/pages")
    List<PageResponse> list(@PathVariable UUID spaceId) { return pages.list(spaceId); }

    @GetMapping("/spaces/{spaceId}/pages/tree")
    List<PageTreeNode> tree(@PathVariable UUID spaceId) { return pages.tree(spaceId); }

    @PostMapping("/spaces/{spaceId}/pages")
    ResponseEntity<PageResponse> create(@PathVariable UUID spaceId, @Valid @RequestBody CreatePageRequest request) {
        PageResponse page = pages.create(spaceId, request);
        return ResponseEntity.created(URI.create("/api/v1/pages/" + page.id()))
                .eTag(com.strangequark.odoc.api.OptimisticConcurrency.etag(page.revision()))
                .body(page);
    }

    @GetMapping("/pages/{pageId}")
    ResponseEntity<PageResponse> get(@PathVariable UUID pageId) {
        PageResponse page = pages.get(pageId);
        return ResponseEntity.ok().eTag(com.strangequark.odoc.api.OptimisticConcurrency.etag(page.revision())).body(page);
    }

    @GetMapping("/pages/{pageId}/history")
    List<PageVersionResponse> history(@PathVariable UUID pageId) { return pages.history(pageId); }

    @PostMapping("/pages/{pageId}/history/{versionId}/restore")
    PageResponse restore(@PathVariable UUID pageId, @PathVariable UUID versionId) {
        return pages.restore(pageId, versionId);
    }

    @PutMapping("/pages/{pageId}")
    ResponseEntity<PageResponse> update(
            @PathVariable UUID pageId,
            @Parameter(name = HttpHeaders.IF_MATCH, required = true, description = "Current page revision ETag.")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdatePageRequest request) {
        PageResponse page = pages.update(pageId, ifMatch, request);
        return ResponseEntity.ok().eTag(com.strangequark.odoc.api.OptimisticConcurrency.etag(page.revision())).body(page);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/pages/{pageId}/move")
    PageResponse move(@PathVariable UUID pageId, @RequestBody MovePageRequest request) {
        return pages.move(pageId, request);
    }

    @DeleteMapping("/pages/{pageId}")
    ResponseEntity<Void> delete(@PathVariable UUID pageId) {
        pages.archive(pageId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/search")
    List<PageResponse> search(@RequestParam String q) { return pages.search(q); }
}
