package com.queueflow.label;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.activity.ActivityService;
import com.queueflow.activity.ActivityType;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.workspace.Workspace;
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

    /**
     * All labels in a workspace, ordered case-insensitively by name
     * (see the repository query for tie-breaking). An unknown
     * workspace is a not-found error, never a silent empty list.
     */
    @Transactional(readOnly = true)
    public List<LabelResponse> getByWorkspace(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ResourceNotFoundException("Workspace not found: " + workspaceId);
        }
        return labelRepository.findAllInWorkspaceSortedByName(workspaceId).stream()
                .map(LabelResponse::from)
                .toList();
    }

    @Transactional
    public TicketResponse addLabelToTicket(UUID ticketId, UUID labelId, UUID actorUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        requireSameWorkspace(ticket, label);
        requireActorSameWorkspace(ticket, actor);

        // Idempotent by design (see Ticket.addLabel): attaching an
        // already-attached label is a no-op, not an error - and no
        // Activity is recorded when nothing actually changed.
        boolean changed = ticket.addLabel(label);
        if (changed) {
            activityService.recordActivity(ActivityType.LABEL_ADDED, null, label.getName(), ticket, actor);
        }

        // No explicit ticketRepository.save(ticket): ticket is managed in
        // this transaction's persistence context, so Hibernate flushes the
        // ticket_labels join-table change via ordinary dirty checking on
        // the collection. recordActivity() joins this same transaction.
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse removeLabelFromTicket(UUID ticketId, UUID labelId, UUID actorUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found: " + labelId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        requireSameWorkspace(ticket, label);
        requireActorSameWorkspace(ticket, actor);

        boolean changed = ticket.removeLabel(label);
        if (changed) {
            activityService.recordActivity(ActivityType.LABEL_REMOVED, label.getName(), null, ticket, actor);
        }

        return TicketResponse.from(ticket);
    }

    private static void requireSameWorkspace(Ticket ticket, Label label) {
        UUID ticketWorkspaceId = ticket.getProject().getWorkspace().getId();
        UUID labelWorkspaceId = label.getWorkspace().getId();
        if (!ticketWorkspaceId.equals(labelWorkspaceId)) {
            throw new BusinessRuleViolationException("Label must belong to the same workspace as the ticket");
        }
    }

    private static void requireActorSameWorkspace(Ticket ticket, User actor) {
        UUID ticketWorkspaceId = ticket.getProject().getWorkspace().getId();
        UUID actorWorkspaceId = actor.getWorkspace().getId();
        if (!ticketWorkspaceId.equals(actorWorkspaceId)) {
            throw new BusinessRuleViolationException("Actor must belong to the same workspace as the ticket");
        }
    }

    private static String normalizeName(String name) {
        return name.trim();
    }
}
