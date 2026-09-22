package com.queueflow.ticket;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // Matches tickets.title VARCHAR(255) - re-checked here because this
    // method can be called directly without going through bean validation
    // (no controller/@Valid layer exists yet).
    private static final int TITLE_MAX_LENGTH = 255;

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, ProjectRepository projectRepository,
            UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
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

        Ticket ticket = new Ticket(ticketNumber, request.title(), request.description(), request.status(),
                request.priority(), project, creator, assignee);
        Ticket saved = ticketRepository.save(ticket);

        // No explicit projectRepository.save(project): project is a managed
        // entity in this transaction's persistence context (loaded, locked,
        // above), so Hibernate's dirty checking picks up the
        // nextTicketNumber mutation from allocateNextTicketNumber() and
        // flushes it in the same transaction/commit as the ticket insert -
        // both succeed together or both roll back together.
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

    @Transactional
    public TicketResponse update(UUID ticketId, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (request.getTitle() != null) {
            ticket.changeTitle(validatedTitle(request.getTitle()));
        }

        PatchField<String> descriptionPatch = request.descriptionPatch();
        if (descriptionPatch.isPresent()) {
            ticket.changeDescription(descriptionPatch.value());
        }

        if (request.getStatus() != null) {
            ticket.changeStatus(request.getStatus());
        }

        if (request.getPriority() != null) {
            ticket.changePriority(request.getPriority());
        }

        PatchField<UUID> assigneeIdPatch = request.assigneeIdPatch();
        if (assigneeIdPatch.isPresent()) {
            UUID assigneeId = assigneeIdPatch.value();
            if (assigneeId == null) {
                ticket.changeAssignee(null);
            } else {
                User assignee = userRepository.findById(assigneeId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assigneeId));
                requireSameWorkspace(ticket.getProject(), assignee,
                        "Assignee must belong to the same workspace as the project");
                ticket.changeAssignee(assignee);
            }
        }

        // No explicit ticketRepository.save(ticket): ticket is a managed
        // entity in this transaction's persistence context, so Hibernate's
        // dirty checking flushes any changed fields (firing the entity's
        // @PreUpdate to refresh updatedAt) automatically at commit. If
        // nothing above actually changed a field, no UPDATE is issued at
        // all and updatedAt correctly stays untouched.
        return TicketResponse.from(ticket);
    }

    private static String validatedTitle(String title) {
        if (title.isBlank()) {
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
