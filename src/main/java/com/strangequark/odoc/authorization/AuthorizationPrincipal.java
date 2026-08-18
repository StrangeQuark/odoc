package com.strangequark.odoc.authorization;

import com.strangequark.odoc.workspace.WorkspaceRole;
import java.util.UUID;

/** A server-derived principal snapshot. Request bodies never choose its role or activity state. */
public record AuthorizationPrincipal(UUID userId, WorkspaceRole workspaceRole, boolean active, boolean instanceAdmin) {
    public static AuthorizationPrincipal anonymous() {
        return new AuthorizationPrincipal(null, null, false, false);
    }

    public static AuthorizationPrincipal guest(UUID userId) {
        return new AuthorizationPrincipal(userId, null, true, false);
    }
}
