package com.strangequark.odoc.space;

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
    private final Clock clock;

    @Autowired
    SpaceService(SpaceRepository spaces) { this(spaces, Clock.systemUTC()); }
    SpaceService(SpaceRepository spaces, Clock clock) { this.spaces = spaces; this.clock = clock; }

    @Transactional(readOnly = true)
    List<SpaceResponse> list() {
        return spaces.findAllByOrderByNameAsc().stream().map(SpaceResponse::from).toList();
    }

    @Transactional
    SpaceResponse create(CreateSpaceRequest request) {
        String key = request.key().trim().toUpperCase(Locale.ROOT);
        if (spaces.findByKey(key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A space with that key already exists.");
        }
        Instant now = clock.instant();
        Space space = new Space(UUID.randomUUID(), key, request.name().trim(), request.description().trim(), now);
        try {
            return SpaceResponse.from(spaces.saveAndFlush(space));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A space with that key already exists.", exception);
        }
    }
}
