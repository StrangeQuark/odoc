package com.strangequark.odoc.workspace;

import java.util.UUID;

/** A workspace visible to the current signed-in member. */
public record WorkspaceResponse(UUID id, String name, WorkspaceRole role) {
    static WorkspaceResponse from(Workspace workspace, WorkspaceMembership membership) {
        return new WorkspaceResponse(workspace.id(), workspace.name(), membership.role());
    }
}
