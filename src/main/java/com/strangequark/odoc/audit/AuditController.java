package com.strangequark.odoc.audit;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/audit-events")
class AuditController {
    private final AuditService audits;
    AuditController(AuditService audits) { this.audits = audits; }
    @GetMapping
    AuditEventPageResponse page(@PathVariable UUID workspaceId, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return audits.page(workspaceId, cursor, limit);
    }
}
