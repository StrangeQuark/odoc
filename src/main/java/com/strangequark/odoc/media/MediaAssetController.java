package com.strangequark.odoc.media;

import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class MediaAssetController {
    private final MediaAssetService assets;

    MediaAssetController(MediaAssetService assets) { this.assets = assets; }

    @PostMapping(path = "/api/v1/spaces/{spaceId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MediaAssetResponse upload(@PathVariable UUID spaceId, @RequestParam("file") MultipartFile file) {
        return assets.upload(spaceId, file);
    }

    @GetMapping("/api/v1/media/{id}")
    ResponseEntity<ByteArrayResource> get(@PathVariable UUID id) {
        MediaAsset asset = assets.get(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + asset.filename() + "\"")
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .contentLength(asset.sizeBytes())
                .body(new ByteArrayResource(asset.content()));
    }

    @DeleteMapping("/api/v1/media/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { assets.deleteIfUnreferenced(id); }
}
