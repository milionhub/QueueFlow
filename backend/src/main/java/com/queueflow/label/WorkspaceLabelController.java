package com.queueflow.label;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.label.dto.LabelResponse;

/**
 * A workspace's labels, addressed as a sub-resource of the workspace.
 * Returns the service's list as-is: ordering (name, then id) and the
 * unknown-workspace check both live in LabelService.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/labels")
public class WorkspaceLabelController {

    private final LabelService labelService;

    public WorkspaceLabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping
    public List<LabelResponse> getByWorkspace(@PathVariable UUID workspaceId) {
        return labelService.getByWorkspace(workspaceId);
    }
}
