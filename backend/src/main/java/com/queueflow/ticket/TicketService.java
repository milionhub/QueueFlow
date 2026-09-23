package com.queueflow.ticket;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.activity.ActivityService;
import com.queueflow.activity.ActivityType;
import com.queueflow.common.PatchField;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.ticket.dto.UpdateTicketRequest;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;

@Service
public class TicketService {

    // Matches tickets.title VARCHAR(255) - re-checked here (in both create
    // and update) because these methods can be called directly without
    // going through bean validation (no controller/@Valid layer exists yet).
    private static final int TITLE_MAX_LENGTH = 255;

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public TicketService(TicketRepository ticketRepository, ProjectRepository projectRepository,
            UserRepository userRepository, ActivityService activityService) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
        // Validated before taking the project row lock below, so an invalid
        // request never blocks concurrent ticket creation on that project.
        String title = validatedTitle(request.title());

        Project project = projectRepository.findByIdForUpdate(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.projectId()));

        User creator = userRepository.findById(request.creatorId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.creatorId()));
        requireSameWorkspace(project, creator, "Creator must belong to the same workspace as the project");

        User assignee = null;
        if (request.assigneeId() != null) {
            assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.assigneeId()));
            requireSameWorkspace(project, assignee, "Assignee must belong to the same workspace as the project");
        }

        // project was loaded under a pessimistic write lock above, so this
        // allocation is safe from concurrent allocation on the same row.
        long ticketNumber = project.allocateNextTicketNumber();

        Ticket ticket = new Ticket(ticketNumber, title, request.description(), request.status(),
                request.priority(), project, creator, assignee);
        Ticket saved = ticketRepository.save(ticket);

        // No explicit projectRepository.save(project): project is a managed
        // entity in this transaction's persistence context (loaded, locked,
        // above), so Hibernate's dirty checking picks up the
        // nextTicketNumber mutation from allocateNextTicketNumber() and
        // flushes it in the same transaction/commit as the ticket insert -
        // both succeed together or both roll back together.
        //
        // recordActivity() joins this same transaction (default REQUIRED
        // propagation): if it fails, the ticket insert and the counter
        // increment above roll back with it - no separate transaction.
        activityService.recordActivity(ActivityType.TICKET_CREATED, null, null, saved, creator);

        return TicketResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse getByProjectAndNumber(UUID projectId, long ticketNumber) {
        Ticket ticket = ticketRepository.findByProjectIdAndTicketNumber(projectId, ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ticket not found: project " + projectId + ", number " + ticketNumber));
        return TicketResponse.from(ticket);
    }

    /**
     * All tickets in a project, ordered by ticket number. An unknown
     * project is a not-found error, never a silent empty list. Mapping
     * happens here, inside the transaction, because TicketResponse reads
     * the (lazy) project's key for displayKey/projectKey.
     */
    @Transactional(readOnly = true)
    public List<TicketResponse> getByProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
        return ticketRepository.findByProjectIdOrderByTicketNumberAsc(projectId).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional
    public TicketResponse update(UUID ticketId, UUID actorUserId, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        requireSameWorkspace(ticket.getProject(), actor, "Actor must belong to the same workspace as the ticket");

        if (request.getTitle() != null) {
            String newTitle = validatedTitle(request.getTitle());
            String oldTitle = ticket.getTitle();
            if (!Objects.equals(oldTitle, newTitle)) {
                ticket.changeTitle(newTitle);
                activityService.recordActivity(ActivityType.TITLE_CHANGED, oldTitle, newTitle, ticket, actor);
            }
        }

        PatchField<String> descriptionPatch = request.descriptionPatch();
        if (descriptionPatch.isPresent()) {
            String newDescription = descriptionPatch.value();
            String oldDescription = ticket.getDescription();
            if (!Objects.equals(oldDescription, newDescription)) {
                ticket.changeDescription(newDescription);
                activityService.recordActivity(
                        ActivityType.DESCRIPTION_CHANGED, oldDescription, newDescription, ticket, actor);
            }
        }

        if (request.getStatus() != null) {
            TicketStatus newStatus = request.getStatus();
            TicketStatus oldStatus = ticket.getStatus();
            if (oldStatus != newStatus) {
                ticket.changeStatus(newStatus);
                activityService.recordActivity(
                        ActivityType.STATUS_CHANGED, oldStatus.name(), newStatus.name(), ticket, actor);
            }
        }

        if (request.getPriority() != null) {
            TicketPriority newPriority = request.getPriority();
            TicketPriority oldPriority = ticket.getPriority();
            if (oldPriority != newPriority) {
                ticket.changePriority(newPriority);
                activityService.recordActivity(
                        ActivityType.PRIORITY_CHANGED, oldPriority.name(), newPriority.name(), ticket, actor);
            }
        }

        PatchField<UUID> assigneeIdPatch = request.assigneeIdPatch();
        if (assigneeIdPatch.isPresent()) {
            UUID newAssigneeId = assigneeIdPatch.value();
            User oldAssignee = ticket.getAssignee();
            UUID oldAssigneeId = oldAssignee != null ? oldAssignee.getId() : null;

            if (!Objects.equals(oldAssigneeId, newAssigneeId)) {
                if (newAssigneeId == null) {
                    ticket.changeAssignee(null);
                    activityService.recordActivity(
                            ActivityType.ASSIGNEE_CHANGED, oldAssigneeId.toString(), null, ticket, actor);
                } else {
                    User newAssignee = userRepository.findById(newAssigneeId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + newAssigneeId));
                    requireSameWorkspace(ticket.getProject(), newAssignee,
                            "Assignee must belong to the same workspace as the project");
                    ticket.changeAssignee(newAssignee);
                    activityService.recordActivity(ActivityType.ASSIGNEE_CHANGED,
                            oldAssigneeId != null ? oldAssigneeId.toString() : null, newAssigneeId.toString(),
                            ticket, actor);
                }
            }
        }

        // No explicit ticketRepository.save(ticket): ticket is a managed
        // entity in this transaction's persistence context, so Hibernate's
        // dirty checking detects any changed fields. If nothing above
        // actually changed a field, no UPDATE is issued at all and
        // updatedAt correctly stays untouched. Each recordActivity call
        // joins this same transaction.
        //
        // The explicit flush() (not a save) makes that dirty-check UPDATE
        // happen now rather than at commit, so the entity's @PreUpdate has
        // already refreshed updatedAt before the response is built below -
        // otherwise the returned DTO would carry the stale pre-update
        // value. It stays inside this same transaction.
        ticketRepository.flush();
        return TicketResponse.from(ticket);
    }

    private static String validatedTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleViolationException("title must not be blank");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new BusinessRuleViolationException("title must be at most " + TITLE_MAX_LENGTH + " characters");
        }
        return title;
    }

    private static void requireSameWorkspace(Project project, User user, String message) {
        UUID projectWorkspaceId = project.getWorkspace().getId();
        UUID userWorkspaceId = user.getWorkspace().getId();
        if (!projectWorkspaceId.equals(userWorkspaceId)) {
            throw new BusinessRuleViolationException(message);
        }
    }
}
