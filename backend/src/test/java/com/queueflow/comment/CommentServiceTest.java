package com.queueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.comment.dto.CreateCommentRequest;
import com.queueflow.comment.dto.UpdateCommentRequest;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.Project;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;

/**
 * Fast unit tests with mocked repositories - real DB persistence/constraint
 * behavior is covered separately by CommentServiceIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, ticketRepository, userRepository);
    }

    private static Workspace persistedWorkspace(UUID id) {
        Workspace workspace = new Workspace("Acme Inc.");
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static Project persistedProject(UUID id, Workspace workspace) {
        Project project = new Project("Project", "ECOM", null, workspace);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private static User persistedUser(UUID id, String name, Workspace workspace) {
        User user = new User(name, "user-" + id + "@example.com", "hash", UserRole.MEMBER, workspace);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Ticket persistedTicket(UUID id, Project project, User creator) {
        Ticket ticket = new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                project, creator, null);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    private static Comment persistedComment(UUID id, String content, Ticket ticket, User author,
            OffsetDateTime timestamp) {
        Comment comment = new Comment(content, ticket, author);
        ReflectionTestUtils.setField(comment, "id", id);
        ReflectionTestUtils.setField(comment, "createdAt", timestamp);
        ReflectionTestUtils.setField(comment, "updatedAt", timestamp);
        return comment;
    }

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    @Test
    void createSucceedsAndSavesCommentWithCorrectFields() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada Lovelace", workspace);

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));

        UUID generatedId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "id", generatedId);
            ReflectionTestUtils.setField(argument, "createdAt", timestamp);
            ReflectionTestUtils.setField(argument, "updatedAt", timestamp);
            return argument;
        });

        CommentResponse response = commentService.create(
                new CreateCommentRequest(ticket.getId(), author.getId(), "Looking into this now"));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        Comment saved = captor.getValue();
        assertThat(saved.getTicket()).isSameAs(ticket);
        assertThat(saved.getAuthor()).isSameAs(author);
        assertThat(saved.getContent()).isEqualTo("Looking into this now");

        assertThat(response).isEqualTo(new CommentResponse(
                generatedId, "Looking into this now", ticket.getId(), author.getId(), "Ada Lovelace",
                timestamp, timestamp));
    }

    @Test
    void createThrowsResourceNotFoundExceptionWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(new CreateCommentRequest(ticketId, authorId, "Hello")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createThrowsResourceNotFoundExceptionWhenAuthorMissing() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        UUID authorId = UUID.randomUUID();

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(authorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(new CreateCommentRequest(ticket.getId(), authorId, "Hello")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(authorId.toString());

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createThrowsBusinessRuleViolationExceptionWhenAuthorInDifferentWorkspace() {
        Workspace ticketWorkspace = persistedWorkspace(UUID.randomUUID());
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), ticketWorkspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", ticketWorkspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Outsider", otherWorkspace);

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> commentService.create(
                new CreateCommentRequest(ticket.getId(), author.getId(), "Hello")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("workspace");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createThrowsBusinessRuleViolationExceptionWhenContentBlank() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> commentService.create(
                new CreateCommentRequest(ticket.getId(), author.getId(), "   ")))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createThrowsBusinessRuleViolationExceptionWhenContentNull() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> commentService.create(
                new CreateCommentRequest(ticket.getId(), author.getId(), null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(commentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // READ
    // ---------------------------------------------------------------

    @Test
    void getByIdReturnsMappedResponse() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        UUID commentId = UUID.randomUUID();
        Comment comment = persistedComment(commentId, "Hello", ticket, author, OffsetDateTime.now());

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        CommentResponse response = commentService.getById(commentId);

        assertThat(response.id()).isEqualTo(commentId);
        assertThat(response.authorName()).isEqualTo("Ada");
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getById(commentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(commentId.toString());
    }

    @Test
    void getByTicketVerifiesTicketExistsBeforeQuerying() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment first = persistedComment(UUID.randomUUID(), "First", ticket, author, OffsetDateTime.now());
        Comment second = persistedComment(UUID.randomUUID(), "Second", ticket, author, OffsetDateTime.now());

        when(ticketRepository.existsById(ticket.getId())).thenReturn(true);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()))
                .thenReturn(List.of(first, second));

        List<CommentResponse> responses = commentService.getByTicket(ticket.getId());

        assertThat(responses).extracting(CommentResponse::content).containsExactly("First", "Second");
        verify(ticketRepository).existsById(ticket.getId());
    }

    @Test
    void getByTicketThrowsResourceNotFoundExceptionWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.existsById(ticketId)).thenReturn(false);

        assertThatThrownBy(() -> commentService.getByTicket(ticketId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());

        verify(commentRepository, never()).findByTicketIdOrderByCreatedAtAsc(any());
    }

    // ---------------------------------------------------------------
    // UPDATE
    // ---------------------------------------------------------------

    @Test
    void updateSucceedsWhenActorIsAuthor() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        CommentResponse response = commentService.update(
                comment.getId(), author.getId(), new UpdateCommentRequest("Updated content"));

        assertThat(comment.getContent()).isEqualTo("Updated content");
        assertThat(response.content()).isEqualTo("Updated content");
    }

    @Test
    void updateThrowsResourceNotFoundExceptionWhenCommentMissing() {
        UUID commentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(commentId, actorId, new UpdateCommentRequest("New")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(commentId.toString());
    }

    @Test
    void updateThrowsBusinessRuleViolationExceptionWhenActorIsNotAuthor() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());
        UUID differentActorId = UUID.randomUUID();

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(
                comment.getId(), differentActorId, new UpdateCommentRequest("Updated")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("author");

        assertThat(comment.getContent()).isEqualTo("Original");
    }

    @Test
    void updateThrowsBusinessRuleViolationExceptionWhenContentBlank() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(
                comment.getId(), author.getId(), new UpdateCommentRequest("   ")))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(comment.getContent()).isEqualTo("Original");
    }

    @Test
    void updateNeverChangesAuthorOrTicket() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        commentService.update(comment.getId(), author.getId(), new UpdateCommentRequest("Updated"));

        assertThat(comment.getAuthor()).isSameAs(author);
        assertThat(comment.getTicket()).isSameAs(ticket);
    }

    @Test
    void updateDoesNotCallCommentRepositorySave() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        commentService.update(comment.getId(), author.getId(), new UpdateCommentRequest("Updated"));

        verify(commentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------------

    @Test
    void deleteSucceedsWhenActorIsAuthor() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        commentService.delete(comment.getId(), author.getId());

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(comment);
    }

    @Test
    void deleteThrowsResourceNotFoundExceptionWhenCommentMissing() {
        UUID commentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(commentId, actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(commentId.toString());

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteThrowsBusinessRuleViolationExceptionWhenActorIsNotAuthor() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());
        UUID differentActorId = UUID.randomUUID();

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(comment.getId(), differentActorId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("author");

        verify(commentRepository, never()).delete(any());
    }
}
