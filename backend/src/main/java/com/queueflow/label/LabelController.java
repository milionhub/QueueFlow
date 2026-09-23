package com.queueflow.label;

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

import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over LabelService for the Label resource itself. Name
 * normalization (trim-only, case preserved), workspace existence and
 * duplicate checks all live in the service - the raw name is passed
 * through untouched on both create and lookup. Ticket-label association
 * endpoints live in TicketLabelController.
 */
@RestController
@RequestMapping("/api/labels")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping
    public ResponseEntity<LabelResponse> create(@Valid @RequestBody CreateLabelRequest request) {
        LabelResponse response = labelService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{labelId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{labelId}")
    public LabelResponse getById(@PathVariable UUID labelId) {
        return labelService.getById(labelId);
    }

    @GetMapping("/by-name")
    public LabelResponse getByWorkspaceAndName(@RequestParam UUID workspaceId, @RequestParam String name) {
        return labelService.getByWorkspaceAndName(workspaceId, name);
    }
}
