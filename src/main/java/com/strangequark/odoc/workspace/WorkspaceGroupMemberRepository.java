package com.strangequark.odoc.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface WorkspaceGroupMemberRepository extends JpaRepository<WorkspaceGroupMember, WorkspaceGroupMember.Key> {
    List<WorkspaceGroupMember> findAllByGroupIdOrderByCreatedAtAsc(UUID groupId);
    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);
}
