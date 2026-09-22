package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.queueflow.label.Label;
import com.queueflow.label.LabelRepository;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

import jakarta.persistence.PersistenceException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TicketLabelAssociationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Ticket newTicket(Project project, User creator, long ticketNumber) {
        return ticketRepository.saveAndFlush(
                new Ticket(ticketNumber, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, null));
    }

    @Test
    void labelCanBeAttachedToTicket() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = newTicket(project, creator, 1L);
        Label label = labelRepository.saveAndFlush(new Label("bug", workspace));

        ticket.getLabels().add(label);
        ticketRepository.saveAndFlush(ticket);
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getLabels()).extracting(Label::getId).containsExactly(label.getId());
    }

    @Test
    void multipleLabelsCanBeAttachedToTicket() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = newTicket(project, creator, 1L);
        Label bug = labelRepository.saveAndFlush(new Label("bug", workspace));
        Label urgent = labelRepository.saveAndFlush(new Label("urgent", workspace));

        ticket.getLabels().add(bug);
        ticket.getLabels().add(urgent);
        ticketRepository.saveAndFlush(ticket);
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getLabels()).extracting(Label::getId)
                .containsExactlyInAnyOrder(bug.getId(), urgent.getId());
    }

    @Test
    void sameTicketLabelPairIsRejectedAtDatabaseLevel() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = newTicket(project, creator, 1L);
        Label label = labelRepository.saveAndFlush(new Label("bug", workspace));

        ticket.getLabels().add(label);
        ticketRepository.saveAndFlush(ticket);

        // The Java Set already prevents adding the very same reference twice
        // in-memory; the real guarantee this proves is the composite primary
        // key on ticket_labels rejecting a duplicate (ticket_id, label_id)
        // row even when attempted directly, independent of ORM collection
        // semantics.
        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO ticket_labels (ticket_id, label_id) VALUES (?1, ?2)")
                .setParameter(1, ticket.getId())
                .setParameter(2, label.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void removingLabelFromTicketDoesNotDeleteTheLabel() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = newTicket(project, creator, 1L);
        Label label = labelRepository.saveAndFlush(new Label("bug", workspace));

        ticket.getLabels().add(label);
        ticketRepository.saveAndFlush(ticket);

        ticket.getLabels().remove(label);
        ticketRepository.saveAndFlush(ticket);

        assertThat(ticket.getLabels()).isEmpty();
        assertThat(labelRepository.findById(label.getId())).isPresent();
    }

    @Test
    void labelAssociationSurvivesFlushClearAndReload() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = newTicket(project, creator, 1L);
        Label label = labelRepository.saveAndFlush(new Label("bug", workspace));

        ticket.getLabels().add(label);
        ticketRepository.saveAndFlush(ticket);
        entityManager.flush();
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getLabels()).hasSize(1);
        assertThat(reloaded.getLabels().iterator().next().getId()).isEqualTo(label.getId());
    }
}
