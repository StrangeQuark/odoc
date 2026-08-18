package com.strangequark.odoc.audit;
import java.util.List;
public record AuditEventPageResponse(List<AuditEventResponse> items, String nextCursor) {}
