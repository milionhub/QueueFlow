package com.queueflow.project;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByWorkspaceIdAndKey(UUID workspaceId, String key);

    Optional<Project> findByWorkspaceIdAndKey(UUID workspaceId, String key);
}
