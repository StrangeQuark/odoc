package com.strangequark.odoc.workspace;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {}
