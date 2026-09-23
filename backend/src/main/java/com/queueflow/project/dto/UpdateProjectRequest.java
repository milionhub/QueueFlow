package com.queueflow.project.dto;

import com.queueflow.common.PatchField;

import jakarta.validation.constraints.Size;

/**
 * PATCH-style partial update for a Project. Same design as
 * UpdateTicketRequest.
 *
 * name: null - whether the JSON key was omitted or explicitly set to null -
 * always means "do not change". The column is NOT NULL, so there is no
 * valid "clear" state; a plain nullable field is sufficient.
 *
 * description: "omitted" and "explicitly null" are different, meaningful
 * states (leave unchanged vs. clear the value), so it is PatchField-typed.
 * See {@link PatchField} for why this class is intentionally NOT a record.
 *
 * There is deliberately no key property: a project's key is immutable
 * after creation.
 */
public class UpdateProjectRequest {

    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    private PatchField<String> description = PatchField.undefined();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Named deliberately not to match "description" as a JavaBean getter,
     * for the same Jackson reason as UpdateTicketRequest.descriptionPatch().
     */
    public PatchField<String> descriptionPatch() {
        return description;
    }

    public void setDescription(String description) {
        this.description = PatchField.of(description);
    }
}
