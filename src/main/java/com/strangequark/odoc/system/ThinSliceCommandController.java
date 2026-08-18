package com.strangequark.odoc.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deliberately test-only Phase 0 command surface.
 *
 * <p>It proves the shared validation, request-ID, generated-contract, and idempotency behavior
 * before real workspace or page commands exist. It is never loaded without the explicit
 * {@code thin-slice} profile and must not become a production/domain API.
 */
@Profile("thin-slice")
@RestController
@RequestMapping("/api/v1/test/commands")
@Tag(name = "Phase 0 test command")
public class ThinSliceCommandController {
    private final Map<String, StoredCommand> commands = new ConcurrentHashMap<>();

    @PostMapping("/echo")
    @Operation(
            summary = "Execute the Phase 0 idempotency contract command",
            description = "Test-profile-only endpoint. A repeated Idempotency-Key with the same payload replays the original response.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Command accepted for the first time",
                content = @Content(schema = @Schema(implementation = ThinSliceCommandResponse.class))),
        @ApiResponse(
                responseCode = "200",
                description = "Previously accepted command replayed",
                content = @Content(schema = @Schema(implementation = ThinSliceCommandResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid command or idempotency key"),
        @ApiResponse(responseCode = "409", description = "Idempotency key was reused with a different payload")
    })
    public ResponseEntity<ThinSliceCommandResponse> echo(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ThinSliceCommandRequest request) {
        if (!idempotencyKey.matches("[A-Za-z0-9._-]{8,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 8-128 safe characters.");
        }

        StoredCommand current = new StoredCommand(request.message().trim(), UUID.randomUUID(), Instant.now());
        StoredCommand existing = commands.putIfAbsent(idempotencyKey, current);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(response(current));
        }
        if (!existing.message().equals(current.message())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different request.");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Idempotency-Replayed", "true")
                .body(response(existing));
    }

    private ThinSliceCommandResponse response(StoredCommand command) {
        return new ThinSliceCommandResponse(command.executionId(), command.message(), command.createdAt());
    }

    public record ThinSliceCommandRequest(@NotBlank @Size(max = 240) String message) {}

    public record ThinSliceCommandResponse(UUID executionId, String message, Instant createdAt) {}

    private record StoredCommand(String message, UUID executionId, Instant createdAt) {}
}
