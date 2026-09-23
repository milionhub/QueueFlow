package com.queueflow.project;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over ProjectService. Key normalization, workspace
 * existence and duplicate-key checks all live in the service - the raw
 * key is passed through untouched on both create and lookup.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{projectId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getById(@PathVariable UUID projectId) {
        return projectService.getById(projectId);
    }

    @GetMapping("/by-key")
    public ProjectResponse getByWorkspaceAndKey(@RequestParam UUID workspaceId, @RequestParam String key) {
        return projectService.getByWorkspaceAndKey(workspaceId, key);
    }
}
