package com.strangequark.odoc.space;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces")
public class SpaceController {
    private final SpaceService spaces;

    SpaceController(SpaceService spaces) { this.spaces = spaces; }

    @GetMapping
    List<SpaceResponse> list() { return spaces.list(); }

    @PostMapping
    ResponseEntity<SpaceResponse> create(@Valid @RequestBody CreateSpaceRequest request) {
        SpaceResponse space = spaces.create(request);
        return ResponseEntity.created(URI.create("/api/v1/spaces/" + space.id())).body(space);
    }

    @GetMapping("/{spaceId}")
    SpaceResponse get(@PathVariable UUID spaceId) { return spaces.get(spaceId); }

    @PutMapping("/{spaceId}")
    SpaceResponse update(@PathVariable UUID spaceId, @Valid @RequestBody UpdateSpaceRequest request) {
        return spaces.update(spaceId, request);
    }

    @DeleteMapping("/{spaceId}")
    ResponseEntity<Void> delete(@PathVariable UUID spaceId) {
        spaces.delete(spaceId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
