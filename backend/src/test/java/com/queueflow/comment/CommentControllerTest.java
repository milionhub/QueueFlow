package com.queueflow.comment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.comment.dto.CreateCommentRequest;
import com.queueflow.comment.dto.UpdateCommentRequest;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;

/**
 * Web-layer slice covering both CommentService-backed controllers
 * (CommentController and TicketCommentController) with CommentService
 * mocked, same approach as the other controller tests. Author-only
 * edit/delete and workspace checks are service behavior, covered by
 * CommentServiceTest / CommentServiceIntegrationTest, not here.
 */
@WebMvcTest({CommentController.class, TicketCommentController.class})
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();

    private static CommentResponse commentResponse(UUID id, String content, OffsetDateTime createdAt) {
        return new CommentResponse(id, content, TICKET_ID, AUTHOR_ID, "Ada Lovelace", createdAt, createdAt);
    }

    private static CommentResponse commentResponse(UUID id, String content) {
        return commentResponse(id, content, OffsetDateTime.parse("2026-09-23T10:15:30Z"));
    }

    /** Only DTO fields: no nested Comment/Ticket/User entities, no passwordHash. */
    private static void expectNoEntityLeakage(ResultActions result, String root) throws Exception {
        result.andExpect(jsonPath(root + ".ticket").doesNotExist())
                .andExpect(jsonPath(root + ".author").doesNotExist())
                .andExpect(jsonPath(root + ".passwordHash").doesNotExist());
    }

    private void postExpectingBadRequest(String json) throws Exception {
        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).create(any());
    }

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    @Test
    void postValidRequestReturns201WithBodyAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(commentService.create(any())).thenReturn(commentResponse(id, "Looking into this now"));

        ResultActions result = mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId": "%s", "authorId": "%s", "content": "Looking into this now"}
                                """.formatted(TICKET_ID, AUTHOR_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/comments/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.content").value("Looking into this now"))
                .andExpect(jsonPath("$.ticketId").value(TICKET_ID.toString()))
                .andExpect(jsonPath("$.authorId").value(AUTHOR_ID.toString()))
                .andExpect(jsonPath("$.authorName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        expectNoEntityLeakage(result, "$");
    }

    @Test
    void postDelegatesExactDeserializedRequestToService() throws Exception {
        when(commentService.create(any())).thenReturn(commentResponse(UUID.randomUUID(), "  Raw content  "));

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId": "%s", "authorId": "%s", "content": "  Raw content  "}
                                """.formatted(TICKET_ID, AUTHOR_ID)))
                .andExpect(status().isCreated());

        verify(commentService).create(new CreateCommentRequest(TICKET_ID, AUTHOR_ID, "  Raw content  "));
    }

    @Test
    void postBlankContentIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"ticketId": "%s", "authorId": "%s", "content": "   "}
                """.formatted(TICKET_ID, AUTHOR_ID));
    }

    @Test
    void postMissingAuthorIdIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"ticketId": "%s", "content": "Hello"}
                """.formatted(TICKET_ID));
    }

    @Test
    void postMissingTicketIdIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"authorId": "%s", "content": "Hello"}
                """.formatted(AUTHOR_ID));
    }

    // ---------------------------------------------------------------
    // GET BY ID
    // ---------------------------------------------------------------

    @Test
    void getByIdReturns200WithExpectedJsonAndDelegatesExactUuid() throws Exception {
        UUID id = UUID.randomUUID();
        when(commentService.getById(id)).thenReturn(commentResponse(id, "Hello"));

        ResultActions result = mockMvc.perform(get("/api/comments/{commentId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.content").value("Hello"))
                .andExpect(jsonPath("$.authorName").value("Ada Lovelace"));
        expectNoEntityLeakage(result, "$");

        verify(commentService).getById(id);
    }

    // ---------------------------------------------------------------
    // GET BY TICKET
    // ---------------------------------------------------------------

    @Test
    void getByTicketReturnsServiceListInExactlyTheServiceProvidedOrder() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        // Deliberately NOT sorted by createdAt or id: if the controller
        // re-sorted anything, the JSON order would differ from this list.
        when(commentService.getByTicket(TICKET_ID)).thenReturn(List.of(
                commentResponse(first, "first", OffsetDateTime.parse("2026-09-23T12:00:00Z")),
                commentResponse(second, "second", OffsetDateTime.parse("2026-09-23T09:00:00Z")),
                commentResponse(third, "third", OffsetDateTime.parse("2026-09-23T10:00:00Z"))));

        ResultActions result = mockMvc.perform(get("/api/tickets/{ticketId}/comments", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[1].id").value(second.toString()))
                .andExpect(jsonPath("$[2].id").value(third.toString()))
                .andExpect(jsonPath("$[0].content").value("first"))
                .andExpect(jsonPath("$[0].ticketId").value(TICKET_ID.toString()));
        expectNoEntityLeakage(result, "$[0]");

        verify(commentService).getByTicket(TICKET_ID);
        verifyNoMoreInteractions(commentService);
    }

    @Test
    void getByTicketWithNoCommentsReturns200EmptyArray() throws Exception {
        when(commentService.getByTicket(TICKET_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/{ticketId}/comments", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ---------------------------------------------------------------
    // PATCH
    // ---------------------------------------------------------------

    @Test
    void patchReturns200AndDelegatesExactIdsAndRequest() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        when(commentService.update(commentId, actorUserId, new UpdateCommentRequest("Edited content")))
                .thenReturn(commentResponse(commentId, "Edited content"));

        ResultActions result = mockMvc.perform(patch("/api/comments/{commentId}", commentId)
                        .param("actorUserId", actorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Edited content"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.content").value("Edited content"));
        expectNoEntityLeakage(result, "$");

        verify(commentService).update(commentId, actorUserId, new UpdateCommentRequest("Edited content"));
        verifyNoMoreInteractions(commentService);
    }

    @Test
    void patchWithoutActorUserIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/comments/{commentId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Edited content"}
                                """))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).update(any(), any(), any());
    }

    @Test
    void patchBlankContentIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/comments/{commentId}", UUID.randomUUID())
                        .param("actorUserId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "  "}
                                """))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).update(any(), any(), any());
    }

    // ---------------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------------

    @Test
    void deleteReturns204WithEmptyBodyAndDelegatesExactIds() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .param("actorUserId", actorUserId.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(commentService).delete(commentId, actorUserId);
        verifyNoMoreInteractions(commentService);
    }

    @Test
    void deleteWithoutActorUserIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(delete("/api/comments/{commentId}", UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).delete(any(), any());
    }
}
