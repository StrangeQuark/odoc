package com.strangequark.odoc.space;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
