package com.strangequark.odoc.system;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "name", "Odoc",
                        "status", "ok",
                        "timestamp", Instant.now().toString()));
    }
}
