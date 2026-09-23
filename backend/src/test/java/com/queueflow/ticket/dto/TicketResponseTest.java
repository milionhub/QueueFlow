package com.queueflow.ticket.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.label.Label;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.project.Project;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.User;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;

/**
 * Pure mapping tests for TicketResponse.from(...): the nested labels list
 * and its ordering. Real lazy/batch loading is covered by
 * TicketLabelBatchFetchIntegrationTest.
 */
class TicketResponseTest {

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-09-23T10:15:30Z");

    private final Workspace workspace = withId(new Workspace("Acme Inc."), UUID.randomUUID());
    private final Project project = withId(new Project("E-Commerce", "ECOM", null, workspace), UUID.randomUUID());
    private final User creator = withId(
            new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace), UUID.randomUUID());
    private final User assignee = withId(
            new User("Assignee", "assignee@example.com", "hash", UserRole.MEMBER, workspace), UUID.randomUUID());

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private Label label(String name) {
        Label label = withId(new Label(name, workspace), UUID.randomUUID());
        ReflectionTestUtils.setField(label, "createdAt", TIMESTAMP);
        ReflectionTestUtils.setField(label, "updatedAt", TIMESTAMP);
        return label;
    }

    private Ticket ticket() {
        Ticket ticket = withId(new Ticket(7L, "Fix checkout bug", "Details", TicketStatus.IN_PROGRESS,
                TicketPriority.HIGH, project, creator, assignee), UUID.randomUUID());
        ReflectionTestUtils.setField(ticket, "createdAt", TIMESTAMP);
        ReflectionTestUtils.setField(ticket, "updatedAt", TIMESTAMP);
        return ticket;
    }

    @Test
    void ticketWithoutLabelsMapsToEmptyListAndKeepsAllOtherFieldsUnchanged() {
        Ticket ticket = ticket();

        TicketResponse response = TicketResponse.from(ticket);

        assertThat(response.labels()).isNotNull().isEmpty();
        assertThat(response).isEqualTo(new TicketResponse(ticket.getId(), 7L, "ECOM-7", "Fix checkout bug",
                "Details", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, project.getId(), "ECOM",
                creator.getId(), assignee.getId(), TIMESTAMP, TIMESTAMP, List.of()));
    }

    @Test
    void allLabelsArePresentAsDtosInCaseInsensitiveNameOrder() {
        Ticket ticket = ticket();
        // Attached in a deliberately unsorted, mixed-case order, including a
        // case-only pair ("Bug"/"bug"), which label uniqueness allows.
        Label urgent = label("urgent");
        Label bugLower = label("bug");
        Label frontend = label("Frontend");
        Label api = label("api");
        Label bugUpper = label("Bug");
        for (Label label : List.of(urgent, bugLower, frontend, api, bugUpper)) {
            ticket.addLabel(label);
        }

        TicketResponse response = TicketResponse.from(ticket);

        // lower(name), then exact name ("Bug" < "bug"), then id.
        assertThat(response.labels()).extracting(LabelResponse::name)
                .containsExactly("api", "Bug", "bug", "Frontend", "urgent");
        assertThat(response.labels().get(0)).isEqualTo(LabelResponse.from(api));
        assertThat(response.labels()).extracting(LabelResponse::workspaceId).containsOnly(workspace.getId());
    }

    @Test
    void mappingNeitherReordersNorMutatesTheEntityCollection() {
        Ticket ticket = ticket();
        Label urgent = label("urgent");
        Label api = label("api");
        ticket.addLabel(urgent);
        ticket.addLabel(api);

        TicketResponse response = TicketResponse.from(ticket);

        assertThat(ticket.getLabels()).containsExactly(urgent, api);
        assertThatThrownBy(() -> response.labels().add(LabelResponse.from(urgent)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullLabelsAreNormalizedToAnEmptyList() {
        TicketResponse response = new TicketResponse(UUID.randomUUID(), 1L, "ECOM-1", "Title", null,
                TicketStatus.TODO, TicketPriority.LOW, UUID.randomUUID(), "ECOM", UUID.randomUUID(), null,
                TIMESTAMP, TIMESTAMP, null);

        assertThat(response.labels()).isNotNull().isEmpty();
    }
}
