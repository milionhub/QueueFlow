package com.queueflow.label;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.activity.ActivityService;
import com.queueflow.activity.ActivityType;
import com.queueflow.common.exception.InvalidRelationshipException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceAccess;
import com.queueflow.workspace.WorkspaceRepository;

@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public LabelService(LabelRepository labelRepository, WorkspaceRepository workspaceRepository,
            TicketRepository ticketRepository, UserRepository userRepository, ActivityService activityService) {
        this.labelRepository = labelRepository;
        this.workspaceRepository = workspaceRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
    }

    /** Always created in the caller's workspace: the request cannot choose another. */
    @Transactional
    public LabelResponse create(AuthenticatedUser actor, CreateLabelRequest request) {
        String normalizedName = normalizeName(request.name());

        // Business-level pre-check for a clean error on the normal path.
        // Not the concurrency guarantee - labels.UNIQUE(workspace_id, name)
        // remains the final protection, same reasoning as ProjectService.
        if (labelRepository.existsByWorkspaceIdAndName(actor.workspaceId(), normalizedName)) {
            throw new ResourceAlreadyExistsException("Label already exists in workspace: " + normalizedName);
        }
        // A reference, no query: the caller's workspace was loaded to authenticate them.
        Workspace workspace = workspaceRepository.getReferenceById(actor.workspaceId());

        Label label = new Label(normalizedName, workspace);
        Label saved = labelRepository.save(label);
        return LabelResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public LabelResponse getById(AuthenticatedUser actor, UUID labelId) {
        Label label = labelRepository.findByIdAndWorkspaceId(labelId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));
        return LabelResponse.from(label);
    }

    /** Names are unique per workspace, so the name is looked up in the caller's workspace only. */
    @Transactional(readOnly = true)
    public LabelResponse getByName(AuthenticatedUser actor, String name) {
        String normalizedName = normalizeName(name);
        Label label = labelRepository.findByWorkspaceIdAndName(actor.workspaceId(), normalizedName)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found with name: " + normalizedName));
        return LabelResponse.from(label);
    }

    /**
     * All labels in a workspace, ordered case-insensitively by name
     * (see the repository query for tie-breaking). Any workspace other than
     * the caller's is not found, never a silent empty list.
     */
    @Transactional(readOnly = true)
    public List<LabelResponse> getByWorkspace(AuthenticatedUser actor, UUID workspaceId) {
        WorkspaceAccess.requireOwnWorkspace(actor, workspaceId);
        return labelRepository.findAllInWorkspaceSortedByName(actor.workspaceId()).stream()
                .map(LabelResponse::from)
                .toList();
    }

    @Transactional
    public TicketResponse addLabelToTicket(AuthenticatedUser actor, UUID ticketId, UUID labelId) {
        // Both sides are looked up inside the caller's workspace, ticket
        // first: anything outside it is simply not found, and each message
        // names only the id the caller sent.
        Ticket ticket = ticketRepository.findByIdAndProjectWorkspaceId(ticketId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Label label = labelRepository.findByIdAndWorkspaceId(labelId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));
        requireSameWorkspace(ticket, label);
        User actingUser = userRepository.getReferenceById(actor.userId());

        // Idempotent by design (see Ticket.addLabel): attaching an
        // already-attached label is a no-op, not an error - and no
        // Activity is recorded when nothing actually changed.
        boolean changed = ticket.addLabel(label);
        if (changed) {
            activityService.recordActivity(ActivityType.LABEL_ADDED, null, label.getName(), ticket, actingUser);
        }

        // No explicit ticketRepository.save(ticket): ticket is managed in
        // this transaction's persistence context, so Hibernate flushes the
        // ticket_labels join-table change via ordinary dirty checking on
        // the collection. recordActivity() joins this same transaction.
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse removeLabelFromTicket(AuthenticatedUser actor, UUID ticketId, UUID labelId) {
        // Both sides are looked up inside the caller's workspace, ticket
        // first: anything outside it is simply not found, and each message
        // names only the id the caller sent.
        Ticket ticket = ticketRepository.findByIdAndProjectWorkspaceId(ticketId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Label label = labelRepository.findByIdAndWorkspaceId(labelId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));
        requireSameWorkspace(ticket, label);
        User actingUser = userRepository.getReferenceById(actor.userId());

        boolean changed = ticket.removeLabel(label);
        if (changed) {
            activityService.recordActivity(ActivityType.LABEL_REMOVED, label.getName(), null, ticket, actingUser);
        }

        return TicketResponse.from(ticket);
    }

    /**
     * Internal safeguard, unreachable through the API: both sides were found
     * in the caller's workspace, so they always match.
     */
    private static void requireSameWorkspace(Ticket ticket, Label label) {
        UUID ticketWorkspaceId = ticket.getProject().getWorkspace().getId();
        UUID labelWorkspaceId = label.getWorkspace().getId();
        if (!ticketWorkspaceId.equals(labelWorkspaceId)) {
            throw new InvalidRelationshipException("Label must belong to the same workspace as the ticket");
        }
    }

    private static String normalizeName(String name) {
        return name.trim();
    }
}
