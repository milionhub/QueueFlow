package com.queueflow.label;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TicketRepository ticketRepository;

    public LabelService(LabelRepository labelRepository, WorkspaceRepository workspaceRepository,
            TicketRepository ticketRepository) {
        this.labelRepository = labelRepository;
        this.workspaceRepository = workspaceRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public LabelResponse create(CreateLabelRequest request) {
        String normalizedName = normalizeName(request.name());

        Workspace workspace = workspaceRepository.findById(request.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + request.workspaceId()));

        // Business-level pre-check for a clean error on the normal path.
        // Not the concurrency guarantee - labels.UNIQUE(workspace_id, name)
        // remains the final protection, same reasoning as ProjectService.
        if (labelRepository.existsByWorkspaceIdAndName(request.workspaceId(), normalizedName)) {
            throw new ResourceAlreadyExistsException("Label already exists in workspace: " + normalizedName);
        }

        Label label = new Label(normalizedName, workspace);
        Label saved = labelRepository.save(label);
        return LabelResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public LabelResponse getById(UUID labelId) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));
        return LabelResponse.from(label);
    }

    @Transactional(readOnly = true)
    public LabelResponse getByWorkspaceAndName(UUID workspaceId, String name) {
        String normalizedName = normalizeName(name);
        Label label = labelRepository.findByWorkspaceIdAndName(workspaceId, normalizedName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Label not found in workspace " + workspaceId + " with name: " + normalizedName));
        return LabelResponse.from(label);
    }

    @Transactional
    public TicketResponse addLabelToTicket(UUID ticketId, UUID labelId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));

        requireSameWorkspace(ticket, label);

        // Idempotent by design (see Ticket.addLabel): attaching an
        // already-attached label is a no-op, not an error. The boolean
        // result isn't used yet - Phase 1.6F will use it to decide whether
        // to record a LABEL_ADDED activity.
        ticket.addLabel(label);

        // No explicit ticketRepository.save(ticket): ticket is managed in
        // this transaction's persistence context, so Hibernate flushes the
        // ticket_labels join-table change via ordinary dirty checking on
        // the collection.
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse removeLabelFromTicket(UUID ticketId, UUID labelId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));

        requireSameWorkspace(ticket, label);

        ticket.removeLabel(label);
        return TicketResponse.from(ticket);
    }

    private static void requireSameWorkspace(Ticket ticket, Label label) {
        UUID ticketWorkspaceId = ticket.getProject().getWorkspace().getId();
        UUID labelWorkspaceId = label.getWorkspace().getId();
        if (!ticketWorkspaceId.equals(labelWorkspaceId)) {
            throw new BusinessRuleViolationException("Label must belong to the same workspace as the ticket");
        }
    }

    private static String normalizeName(String name) {
        return name.trim();
    }
}
