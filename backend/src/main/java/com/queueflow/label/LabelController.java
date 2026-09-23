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

import com.queueflow.config.OpenApiConfig;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over LabelService for the Label resource itself. Name
 * normalization (trim-only, case preserved), workspace existence and
 * duplicate checks all live in the service - the raw name is passed
 * through untouched on both create and lookup. Ticket-label association
 * endpoints live in TicketLabelController.
 */
@Tag(name = "Labels", description = "Workspace labels and their assignment to tickets")
@RestController
@RequestMapping("/api/labels")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @Operation(summary = "Create a label", operationId = "createLabel",
            description = "The name is trimmed and must be unique (case-sensitive) within the workspace.")
    @ApiResponse(responseCode = "201", description = "Label created", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @PostMapping
    public ResponseEntity<LabelResponse> create(@Valid @RequestBody CreateLabelRequest request) {
        LabelResponse response = labelService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{labelId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get a label", operationId = "getLabel")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{labelId}")
    public LabelResponse getById(@PathVariable UUID labelId) {
        return labelService.getById(labelId);
    }

    @Operation(summary = "Get a label by workspace and name", operationId = "getLabelByName")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/by-name")
    public LabelResponse getByWorkspaceAndName(@RequestParam UUID workspaceId,
            @Parameter(description = "Label name; trimmed, case-sensitive") @RequestParam String name) {
        return labelService.getByWorkspaceAndName(workspaceId, name);
    }
}
