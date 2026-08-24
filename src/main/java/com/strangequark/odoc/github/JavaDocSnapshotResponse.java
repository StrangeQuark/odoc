package com.strangequark.odoc.github;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record JavaDocSnapshotResponse(
        UUID id, String sourcePath, String packageName, String typeName, String typeKind,
        String documentation, List<JavaDocMember> members, Instant refreshedAt) {}
