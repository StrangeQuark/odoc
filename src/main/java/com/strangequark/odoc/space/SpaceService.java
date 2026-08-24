package com.strangequark.odoc.space;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import com.strangequark.odoc.authorization.AuthorizationAction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class SpaceService {
    private final SpaceRepository spaces;
    private final WorkspaceAccessService workspaceAccess;
    private final Clock clock;

    @Autowired
    SpaceService(SpaceRepository spaces, WorkspaceAccessService workspaceAccess) {
        this(spaces, workspaceAccess, Clock.systemUTC());
    }
    SpaceService(SpaceRepository spaces, WorkspaceAccessService workspaceAccess, Clock clock) {
        this.spaces = spaces;
        this.workspaceAccess = workspaceAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<SpaceResponse> list() {
        List<UUID> workspaceIds = workspaceAccess.workspaceIdsForCurrentUser();
        if (workspaceIds.isEmpty()) return List.of();
        return spaces.findAllByWorkspaceIdInOrderByNameAsc(workspaceIds).stream().map(SpaceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    SpaceResponse get(UUID spaceId) {
        workspaceAccess.requireSpaceAction(spaceId, AuthorizationAction.SPACE_VIEW);
        return SpaceResponse.from(requireSpace(spaceId));
    }

    @Transactional
    SpaceResponse create(CreateSpaceRequest request) {
        UUID workspaceId = workspaceAccess.defaultWorkspaceForCurrentUser();
        workspaceAccess.requireWorkspaceAction(workspaceId, AuthorizationAction.SPACE_CREATE);
        String key = request.key().trim().toUpperCase(Locale.ROOT);
        if (spaces.findByWorkspaceIdAndKey(workspaceId, key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A space with that key already exists.");
        }
        Instant now = clock.instant();
        String description = request.description() == null ? "" : request.description().trim();
        Space space = new Space(UUID.randomUUID(), workspaceId, key, request.name().trim(), description, now);
        try {
            return SpaceResponse.from(spaces.saveAndFlush(space));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A space with that key already exists.", exception);
        }
    }

    @Transactional
    SpaceResponse update(UUID spaceId, UpdateSpaceRequest request) {
        workspaceAccess.requireSpaceAction(spaceId, AuthorizationAction.SPACE_EDIT_SETTINGS);
        Space space = requireSpace(spaceId);
        String description = request.description() == null ? "" : request.description().trim();
        space.update(request.name().trim(), description, clock.instant());
        return SpaceResponse.from(spaces.saveAndFlush(space));
    }

    @Transactional
    void delete(UUID spaceId) {
        workspaceAccess.requireSpaceAction(spaceId, AuthorizationAction.SPACE_DELETE);
        spaces.delete(requireSpace(spaceId));
    }

    private Space requireSpace(UUID spaceId) {
        return spaces.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found."));
    }
}
