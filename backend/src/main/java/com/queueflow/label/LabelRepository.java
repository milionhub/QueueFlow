package com.queueflow.label;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    Optional<Label> findByWorkspaceIdAndName(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
}
