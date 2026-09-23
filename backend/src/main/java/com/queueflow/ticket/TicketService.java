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
import com.queueflow.common.exception.InvalidRelationshipException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.security.AuthenticatedUser;
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

    /** The creator is the acting user - never a client-supplied id. */
    @Transactional
    public TicketResponse create(AuthenticatedUser actor, CreateTicketRequest request) {
        // Validated before taking the project row lock below, so an invalid
        // request never blocks concurrent ticket creation on that project.
        String title = validatedTitle(request.title());

        // Only a project of the caller's workspace can be found - and locked.
        Project project = projectRepository.findByIdAndWorkspaceIdForUpdate(request.projectId(), actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.projectId()));
        User creator = actingUser(actor);

        User assignee = null;
        if (request.assigneeId() != null) {
            assignee = visibleUser(actor, request.assigneeId());
            if (!inSameWorkspace(project, assignee)) {
                throw new InvalidRelationshipException("Assignee must belong to the same workspace as the project");
            }
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
    public TicketResponse getById(AuthenticatedUser actor, UUID ticketId) {
        return TicketResponse.from(visibleTicket(actor, ticketId));
    }

    /** The project is resolved in the caller's workspace first; only then its ticket numbers. */
    @Transactional(readOnly = true)
    public TicketResponse getByProjectAndNumber(AuthenticatedUser actor, UUID projectId, long ticketNumber) {
        requireVisibleProject(actor, projectId);
        Ticket ticket = ticketRepository.findByProjectIdAndTicketNumber(projectId, ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ticket not found: project " + projectId + ", number " + ticketNumber));
        return TicketResponse.from(ticket);
    }

    /**
     * All tickets in a project, ordered by ticket number. An unknown or
     * foreign project is a not-found error, never a silent empty list. Mapping
     * happens here, inside the transaction, because TicketResponse reads
     * the (lazy) project's key for displayKey/projectKey.
     */
    @Transactional(readOnly = true)
    public List<TicketResponse> getByProject(AuthenticatedUser actor, UUID projectId) {
        requireVisibleProject(actor, projectId);
        return ticketRepository.findByProjectIdOrderByTicketNumberAsc(projectId).stream()
                .map(TicketResponse::from)
                .toList();
    }

    /** Every activity this update records is attributed to the acting user. */
    @Transactional
    public TicketResponse update(AuthenticatedUser actor, UUID ticketId, UpdateTicketRequest request) {
        Ticket ticket = visibleTicket(actor, ticketId);
        User actingUser = actingUser(actor);

        if (request.getTitle() != null) {
            String newTitle = validatedTitle(request.getTitle());
            String oldTitle = ticket.getTitle();
            if (!Objects.equals(oldTitle, newTitle)) {
                ticket.changeTitle(newTitle);
                activityService.recordActivity(ActivityType.TITLE_CHANGED, oldTitle, newTitle, ticket, actingUser);
            }
        }

        PatchField<String> descriptionPatch = request.descriptionPatch();
        if (descriptionPatch.isPresent()) {
            String newDescription = descriptionPatch.value();
            String oldDescription = ticket.getDescription();
            if (!Objects.equals(oldDescription, newDescription)) {
                ticket.changeDescription(newDescription);
                activityService.recordActivity(
                        ActivityType.DESCRIPTION_CHANGED, oldDescription, newDescription, ticket, actingUser);
            }
        }

        if (request.getStatus() != null) {
            TicketStatus newStatus = request.getStatus();
            TicketStatus oldStatus = ticket.getStatus();
            if (oldStatus != newStatus) {
                ticket.changeStatus(newStatus);
                activityService.recordActivity(
                        ActivityType.STATUS_CHANGED, oldStatus.name(), newStatus.name(), ticket, actingUser);
            }
        }

        if (request.getPriority() != null) {
            TicketPriority newPriority = request.getPriority();
            TicketPriority oldPriority = ticket.getPriority();
            if (oldPriority != newPriority) {
                ticket.changePriority(newPriority);
                activityService.recordActivity(
                        ActivityType.PRIORITY_CHANGED, oldPriority.name(), newPriority.name(), ticket, actingUser);
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
                            ActivityType.ASSIGNEE_CHANGED, oldAssigneeId.toString(), null, ticket, actingUser);
                } else {
                    User newAssignee = visibleUser(actor, newAssigneeId);
                    if (!inSameWorkspace(ticket.getProject(), newAssignee)) {
                        throw new InvalidRelationshipException(
                                "Assignee must belong to the same workspace as the project");
                    }
                    ticket.changeAssignee(newAssignee);
                    activityService.recordActivity(ActivityType.ASSIGNEE_CHANGED,
                            oldAssigneeId != null ? oldAssigneeId.toString() : null, newAssigneeId.toString(),
                            ticket, actingUser);
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

    /** A ticket of the caller's workspace; any other is not found. */
    private Ticket visibleTicket(AuthenticatedUser actor, UUID ticketId) {
        return ticketRepository.findByIdAndProjectWorkspaceId(ticketId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
    }

    private void requireVisibleProject(AuthenticatedUser actor, UUID projectId) {
        if (!projectRepository.existsByIdAndWorkspaceId(projectId, actor.workspaceId())) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
    }

    /** A user (e.g. an assignee) of the caller's workspace; any other is not found. */
    private User visibleUser(AuthenticatedUser actor, UUID userId) {
        return userRepository.findByIdAndWorkspaceId(userId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    /**
     * The acting user as an entity reference for the creator/activity
     * associations. No query: the user was loaded for this very request to
     * authenticate it.
     */
    private User actingUser(AuthenticatedUser actor) {
        return userRepository.getReferenceById(actor.userId());
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

    /**
     * Whether the assignee belongs to the project's workspace; a mismatch is
     * an invalid relationship (InvalidRelationshipException). An internal
     * safeguard, unreachable through the API: both were found in the caller's
     * workspace.
     */
    private static boolean inSameWorkspace(Project project, User user) {
        return project.getWorkspace().getId().equals(user.getWorkspace().getId());
    }
}
