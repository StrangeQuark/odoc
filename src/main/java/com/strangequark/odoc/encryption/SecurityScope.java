package com.strangequark.odoc.encryption;

import java.util.Objects;
import java.util.UUID;

/** A stable instance or workspace scope; it is never inferred from client input. */
public record SecurityScope(SecurityScopeKind kind, UUID id) {
    public SecurityScope {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }
}
