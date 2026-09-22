package com.queueflow.ticket;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;

@Service
public class TicketService {

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

    private static void requireSameWorkspace(Project project, User user, String message) {
        UUID projectWorkspaceId = project.getWorkspace().getId();
        UUID userWorkspaceId = user.getWorkspace().getId();
        if (!projectWorkspaceId.equals(userWorkspaceId)) {
            throw new BusinessRuleViolationException(message);
        }
    }
}
