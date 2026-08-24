package com.strangequark.odoc.page;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import com.strangequark.odoc.authorization.AuthorizationAction;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Resolves a page through its owning workspace before another feature can expose it. */
@Service
public class PageAccessService {
    private final PageRepository pages;
    private final WorkspaceAccessService workspaces;

    PageAccessService(PageRepository pages, WorkspaceAccessService workspaces) {
        this.pages = pages;
        this.workspaces = workspaces;
    }

    @Transactional(readOnly = true)
    public void requireAccessiblePage(UUID pageId) {
        requirePageAction(pageId, AuthorizationAction.PAGE_VIEW);
    }

    /** Resolves a page and applies the requested action through its owning space. */
    @Transactional(readOnly = true)
    public UUID requirePageAction(UUID pageId, AuthorizationAction action) {
        Page page = pages.findById(pageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found."));
        workspaces.requireSpaceAction(page.getSpaceId(), action);
        return page.getSpaceId();
    }
}
