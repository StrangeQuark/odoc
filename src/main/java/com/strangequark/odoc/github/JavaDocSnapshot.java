package com.strangequark.odoc.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "javadoc_snapshots")
class JavaDocSnapshot {
    @Id private UUID id;
    @Column(name = "repository_binding_id", nullable = false) private UUID repositoryBindingId;
    @Column(name = "source_path", nullable = false) private String sourcePath;
    @Column(name = "package_name", nullable = false) private String packageName;
    @Column(name = "type_name", nullable = false) private String typeName;
    @Column(name = "type_kind", nullable = false) private String typeKind;
    @Column(nullable = false) private String documentation;
    @Column(name = "members_json", nullable = false) private String membersJson;
    @Column(name = "refreshed_at", nullable = false) private Instant refreshedAt;

    protected JavaDocSnapshot() {}

    JavaDocSnapshot(UUID id, UUID repositoryBindingId, String sourcePath, ParsedJavaDoc parsed,
                    String membersJson, Instant refreshedAt) {
        this.id = id;
        this.repositoryBindingId = repositoryBindingId;
        this.sourcePath = sourcePath;
        update(parsed, membersJson, refreshedAt);
    }

    UUID id() { return id; }
    UUID repositoryBindingId() { return repositoryBindingId; }
    String sourcePath() { return sourcePath; }
    String packageName() { return packageName; }
    String typeName() { return typeName; }
    String typeKind() { return typeKind; }
    String documentation() { return documentation; }
    String membersJson() { return membersJson; }
    Instant refreshedAt() { return refreshedAt; }

    void update(ParsedJavaDoc parsed, String membersJson, Instant refreshedAt) {
        this.packageName = parsed.packageName();
        this.typeName = parsed.typeName();
        this.typeKind = parsed.typeKind();
        this.documentation = parsed.documentation();
        this.membersJson = membersJson;
        this.refreshedAt = refreshedAt;
    }
}
