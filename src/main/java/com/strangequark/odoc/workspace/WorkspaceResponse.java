package com.strangequark.odoc.workspace;

import java.util.UUID;

/** A workspace visible to the current signed-in member. */
public record WorkspaceResponse(UUID id, String name, WorkspaceRole role, long revision) {
    static WorkspaceResponse from(Workspace workspace, WorkspaceMembership membership) {
        return new WorkspaceResponse(workspace.id(), workspace.name(), membership.role(), workspace.revision());
    }
}
