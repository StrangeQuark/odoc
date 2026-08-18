package com.strangequark.odoc.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workspace_group_members")
@IdClass(WorkspaceGroupMember.Key.class)
class WorkspaceGroupMember {
    @Id @Column(name = "group_id", nullable = false) private UUID groupId;
    @Id @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WorkspaceGroupMember() {}

    WorkspaceGroupMember(UUID groupId, UUID workspaceId, UUID userId, Instant createdAt) {
        this.groupId = groupId;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    UUID groupId() { return groupId; }
    UUID workspaceId() { return workspaceId; }
    UUID userId() { return userId; }
    Instant createdAt() { return createdAt; }

    public static final class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private UUID groupId;
        private UUID userId;

        public Key() {}

        public Key(UUID groupId, UUID userId) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
            this.userId = Objects.requireNonNull(userId, "userId");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return groupId.equals(key.groupId) && userId.equals(key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, userId);
        }
    }
}
