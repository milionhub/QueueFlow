package com.queueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.queueflow.common.exception.ForbiddenOperationException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.Project;
import com.queueflow.security.AuthenticatedUser;
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

    /** The principal the security layer would build for this user. */
    private static AuthenticatedUser actorOf(User user) {
        return new AuthenticatedUser(user.getId(), user.getWorkspace().getId(), user.getRole());
    }

    private static AuthenticatedUser actorWithId(UUID userId) {
        return new AuthenticatedUser(userId, UUID.randomUUID(), UserRole.MEMBER);
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

        when(ticketRepository.findByIdAndProjectWorkspaceId(eq(ticket.getId()), any()))
                .thenReturn(Optional.of(ticket));
        when(userRepository.getReferenceById(author.getId())).thenReturn(author);

        UUID generatedId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "id", generatedId);
            ReflectionTestUtils.setField(argument, "createdAt", timestamp);
            ReflectionTestUtils.setField(argument, "updatedAt", timestamp);
            return argument;
        });

        CommentResponse response = commentService.create(actorOf(author),
                new CreateCommentRequest(ticket.getId(), "Looking into this now"));

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
        when(ticketRepository.findByIdAndProjectWorkspaceId(eq(ticketId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(actorWithId(UUID.randomUUID()),
                new CreateCommentRequest(ticketId, "Hello")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());

        verify(commentRepository, never()).save(any());
    }

    /** The author is always the authenticated actor: attached by reference, never looked up by a request id. */
    @Test
    void createUsesTheAuthenticatedActorAsAuthor() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User actor = persistedUser(UUID.randomUUID(), "Juan", workspace);

        when(ticketRepository.findByIdAndProjectWorkspaceId(eq(ticket.getId()), any()))
                .thenReturn(Optional.of(ticket));
        when(userRepository.getReferenceById(actor.getId())).thenReturn(actor);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentResponse response = commentService.create(actorOf(actor),
                new CreateCommentRequest(ticket.getId(), "Hi"));

        assertThat(response.authorId()).isEqualTo(actor.getId());
        verify(userRepository, never()).findById(any());
    }

    /** A ticket of another workspace is not found for the caller - it cannot be commented on. */
    @Test
    void commentOnATicketOfAnotherWorkspaceIsNotFound() {
        UUID ticketId = UUID.randomUUID();
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        User author = persistedUser(UUID.randomUUID(), "Outsider", otherWorkspace);
        when(ticketRepository.findByIdAndProjectWorkspaceId(ticketId, otherWorkspace.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(actorOf(author), new CreateCommentRequest(ticketId, "Hello")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Ticket not found: " + ticketId);
        verify(userRepository, never()).getReferenceById(any());

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createThrowsBusinessRuleViolationExceptionWhenContentBlank() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);

        when(ticketRepository.findByIdAndProjectWorkspaceId(eq(ticket.getId()), any()))
                .thenReturn(Optional.of(ticket));
        when(userRepository.getReferenceById(author.getId())).thenReturn(author);

        assertThatThrownBy(() -> commentService.create(actorOf(author),
                new CreateCommentRequest(ticket.getId(), "   ")))
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

        when(ticketRepository.findByIdAndProjectWorkspaceId(eq(ticket.getId()), any()))
                .thenReturn(Optional.of(ticket));
        when(userRepository.getReferenceById(author.getId())).thenReturn(author);

        assertThatThrownBy(() -> commentService.create(actorOf(author),
                new CreateCommentRequest(ticket.getId(), null)))
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

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(commentId), any()))
                .thenReturn(Optional.of(comment));

        CommentResponse response = commentService.getById(actorWithId(UUID.randomUUID()), commentId);

        assertThat(response.id()).isEqualTo(commentId);
        assertThat(response.authorName()).isEqualTo("Ada");
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(commentId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getById(actorWithId(UUID.randomUUID()), commentId))
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

        when(ticketRepository.existsByIdAndProjectWorkspaceId(eq(ticket.getId()), any())).thenReturn(true);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()))
                .thenReturn(List.of(first, second));

        List<CommentResponse> responses = commentService.getByTicket(actorWithId(UUID.randomUUID()), ticket.getId());

        assertThat(responses).extracting(CommentResponse::content).containsExactly("First", "Second");
        verify(ticketRepository).existsByIdAndProjectWorkspaceId(eq(ticket.getId()), any());
    }

    @Test
    void getByTicketThrowsResourceNotFoundExceptionWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.existsByIdAndProjectWorkspaceId(eq(ticketId), any())).thenReturn(false);

        assertThatThrownBy(() -> commentService.getByTicket(actorWithId(UUID.randomUUID()), ticketId))
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

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        CommentResponse response = commentService.update(
                actorOf(author), comment.getId(), new UpdateCommentRequest("Updated content"));

        assertThat(comment.getContent()).isEqualTo("Updated content");
        assertThat(response.content()).isEqualTo("Updated content");
    }

    @Test
    void updateThrowsResourceNotFoundExceptionWhenCommentMissing() {
        UUID commentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(commentId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(actorWithId(actorId), commentId,
                new UpdateCommentRequest("New")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(commentId.toString());
    }

    /**
     * The order matters: a comment outside the caller's workspace is not
     * found (404) - only inside the workspace does authorship decide (403).
     */
    @Test
    void editOrDeleteOfACommentOfAnotherWorkspaceIsNotFoundNotForbidden() {
        UUID commentId = UUID.randomUUID();
        AuthenticatedUser outsider = actorWithId(UUID.randomUUID());
        when(commentRepository.findByIdAndTicketProjectWorkspaceId(commentId, outsider.workspaceId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(outsider, commentId, new UpdateCommentRequest("Edited")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Comment not found: " + commentId);
        assertThatThrownBy(() -> commentService.delete(outsider, commentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Comment not found: " + commentId);
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void updateThrowsForbiddenOperationExceptionWhenActorIsNotAuthor() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());
        UUID differentActorId = UUID.randomUUID();

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(
                actorWithId(differentActorId), comment.getId(), new UpdateCommentRequest("Updated")))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("author");

        assertThat(comment.getContent()).isEqualTo("Original");
    }

    @Test
    void anAdminWhoIsNotTheAuthorIsForbiddenToo() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, author);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());
        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), workspace.getId(), UserRole.ADMIN);

        assertThatThrownBy(() -> commentService.update(admin, comment.getId(), new UpdateCommentRequest("Edited")))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> commentService.delete(admin, comment.getId()))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(comment.getContent()).isEqualTo("Original");
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void updateByNonAuthorIsForbiddenEvenWhenTheContentIsAlsoInvalid() {
        // Permission is checked before the content: a non-author gets 403,
        // not a validation error about a comment they may not edit anyway.
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, author);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());
        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(
                actorWithId(UUID.randomUUID()), comment.getId(), new UpdateCommentRequest("   ")))
                .isInstanceOf(ForbiddenOperationException.class);

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

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(
                actorOf(author), comment.getId(), new UpdateCommentRequest("   ")))
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

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        commentService.update(actorOf(author), comment.getId(), new UpdateCommentRequest("Updated"));

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

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        commentService.update(actorOf(author), comment.getId(), new UpdateCommentRequest("Updated"));

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

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        commentService.delete(actorOf(author), comment.getId());

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(comment);
    }

    @Test
    void deleteThrowsResourceNotFoundExceptionWhenCommentMissing() {
        UUID commentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(commentId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(actorWithId(actorId), commentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(commentId.toString());

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteThrowsForbiddenOperationExceptionWhenActorIsNotAuthor() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), workspace);
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User author = persistedUser(UUID.randomUUID(), "Ada", workspace);
        Comment comment = persistedComment(UUID.randomUUID(), "Original", ticket, author, OffsetDateTime.now());
        UUID differentActorId = UUID.randomUUID();

        when(commentRepository.findByIdAndTicketProjectWorkspaceId(eq(comment.getId()), any()))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(actorWithId(differentActorId), comment.getId()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("author");

        verify(commentRepository, never()).delete(any());
    }
}
