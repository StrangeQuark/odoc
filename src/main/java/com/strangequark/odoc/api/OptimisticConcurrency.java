package com.strangequark.odoc.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** HTTP ETag helpers used by mutation endpoints once their revision is public. */
public final class OptimisticConcurrency {
    private OptimisticConcurrency() {}

    public static String etag(long revision) {
        if (revision < 0) throw new IllegalArgumentException("Revision must not be negative.");
        return "\"revision-" + revision + "\"";
    }

    /**
     * Require an exact current ETag before a destructive mutation. `*` has the
     * normal HTTP meaning of an existing resource and is useful for idempotent
     * administrative commands; ordinary clients should send the specific tag.
     */
    public static void requireMatching(String ifMatch, long revision) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "If-Match is required.");
        }
        String current = etag(revision);
        boolean matches = ifMatch.equals("*")
                || ListTokens.containsExact(ifMatch, current);
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Resource revision does not match.");
        }
    }

    private static final class ListTokens {
        private ListTokens() {}

        static boolean containsExact(String header, String expected) {
            for (String candidate : header.split(",")) {
                if (expected.equals(candidate.trim())) return true;
            }
            return false;
        }
    }
}
