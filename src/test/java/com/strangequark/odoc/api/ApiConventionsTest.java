package com.strangequark.odoc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiConventionsTest {
    @Test
    void cursorPageCopiesItemsAndUsesNullOnlyForTheTerminalCursor() {
        List<String> source = new ArrayList<>(List.of("first"));
        CursorPage<String> terminal = new CursorPage<>(source, null);
        source.add("later mutation");

        assertThat(terminal.items()).containsExactly("first");
        assertThat(terminal.nextCursor()).isNull();
    }

    @Test
    void etagsAreQuotedAndMatchingRequiresTheCurrentRevision() {
        assertThat(OptimisticConcurrency.etag(7)).isEqualTo("\"revision-7\"");
        OptimisticConcurrency.requireMatching("\"revision-6\", \"revision-7\"", 7);
        OptimisticConcurrency.requireMatching("*", 7);

        assertThatThrownBy(() -> OptimisticConcurrency.requireMatching(null, 7))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThatThrownBy(() -> OptimisticConcurrency.requireMatching("\"revision-6\"", 7))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.PRECONDITION_FAILED);
    }
}
