package com.taskmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.Task.Priority;
import com.taskmanager.entity.Task.Status;
import com.taskmanager.security.JwtUtil;
import com.taskmanager.security.WithMockCustomUser;
import com.taskmanager.service.TaskService;
import com.taskmanager.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVC slice tests for TaskController.
 * Security is fully active; @WithMockCustomUser provides a CustomUserDetails principal.
 * JwtAuthFilter skips when there is no Authorization header (shouldNotFilter=true).
 * UserService is mocked because SecurityConfig depends on it.
 */
@WebMvcTest(TaskController.class)
@WithMockCustomUser          // applies to all test methods (userId=1, username="testuser")
class TaskControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    /**
     * UserService must be mocked so SecurityConfig (and DaoAuthenticationProvider)
     * can be created in the web-test slice context.
     */
    @MockBean
    private UserService userService;

    /**
     * JwtUtil must be mocked because JwtAuthFilter (picked up by SecurityConfig)
     * requires it as a constructor dependency in the web slice context.
     */
    @MockBean
    private JwtUtil jwtUtil;

    // Helper: build a populated Task (simulates a persisted entity)
    private Task task(Long id, String title, String description, Status status) {
        return task(id, title, description, status, Priority.MEDIUM);
    }

    private Task task(Long id, String title, String description, Status status, Priority priority) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setDescription(description);
        t.setStatus(status);
        t.setPriority(priority);
        t.setUserId(USER_ID);
        t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return t;
    }

    // -------------------------------------------------------
    // GET /api/tasks
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /api/tasks")
    class GetAllTasks {

        @Test
        @DisplayName("returns 200 with a JSON array of tasks")
        void returns200WithTaskList() throws Exception {
            given(taskService.getAllTasks(USER_ID)).willReturn(List.of(
                    task(1L, "Task A", null,      Status.TODO,        Priority.LOW),
                    task(2L, "Task B", "details", Status.IN_PROGRESS, Priority.HIGH)
            ));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id",       is(1)))
                    .andExpect(jsonPath("$[0].title",    is("Task A")))
                    .andExpect(jsonPath("$[0].status",   is("TODO")))
                    .andExpect(jsonPath("$[0].priority", is("LOW")))
                    .andExpect(jsonPath("$[1].id",       is(2)))
                    .andExpect(jsonPath("$[1].status",   is("IN_PROGRESS")))
                    .andExpect(jsonPath("$[1].priority", is("HIGH")));
        }

        @Test
        @DisplayName("returns 200 with an empty array when user has no tasks")
        void returns200EmptyList() throws Exception {
            given(taskService.getAllTasks(USER_ID)).willReturn(List.of());

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // -------------------------------------------------------
    // GET /api/tasks/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /api/tasks/{id}")
    class GetTaskById {

        @Test
        @DisplayName("returns 200 with the task when it belongs to the user")
        void taskExists_returns200() throws Exception {
            given(taskService.getTaskById(1L, USER_ID))
                    .willReturn(Optional.of(task(1L, "Found it", "desc", Status.DONE, Priority.HIGH)));

            mockMvc.perform(get("/api/tasks/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id",          is(1)))
                    .andExpect(jsonPath("$.title",       is("Found it")))
                    .andExpect(jsonPath("$.description", is("desc")))
                    .andExpect(jsonPath("$.status",      is("DONE")))
                    .andExpect(jsonPath("$.priority",    is("HIGH")));
        }

        @Test
        @DisplayName("returns 404 when task does not exist or belongs to another user")
        void taskMissing_returns404() throws Exception {
            given(taskService.getTaskById(99L, USER_ID)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/tasks/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------
    // POST /api/tasks
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /api/tasks")
    class CreateTask {

        @Test
        @DisplayName("returns 201 with the created task body")
        void returns201WithCreatedTask() throws Exception {
            Task input   = task(null, "New Task", "desc", Status.TODO, Priority.MEDIUM);
            Task created = task(5L,   "New Task", "desc", Status.TODO, Priority.MEDIUM);
            given(taskService.createTask(any(Task.class), eq(USER_ID))).willReturn(created);

            mockMvc.perform(post("/api/tasks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id",       is(5)))
                    .andExpect(jsonPath("$.title",    is("New Task")))
                    .andExpect(jsonPath("$.status",   is("TODO")))
                    .andExpect(jsonPath("$.priority", is("MEDIUM")));

            then(taskService).should().createTask(any(Task.class), eq(USER_ID));
        }
    }

    // -------------------------------------------------------
    // PUT /api/tasks/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/tasks/{id}")
    class UpdateTask {

        @Test
        @DisplayName("returns 200 with the updated task when it belongs to the user")
        void taskExists_returns200() throws Exception {
            Task update  = task(null, "Updated", "new desc", Status.IN_PROGRESS, Priority.HIGH);
            Task updated = task(1L,   "Updated", "new desc", Status.IN_PROGRESS, Priority.HIGH);
            given(taskService.updateTask(eq(1L), any(Task.class), eq(USER_ID)))
                    .willReturn(Optional.of(updated));

            mockMvc.perform(put("/api/tasks/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id",       is(1)))
                    .andExpect(jsonPath("$.title",    is("Updated")))
                    .andExpect(jsonPath("$.status",   is("IN_PROGRESS")))
                    .andExpect(jsonPath("$.priority", is("HIGH")));
        }

        @Test
        @DisplayName("returns 404 when task does not exist or belongs to another user")
        void taskMissing_returns404() throws Exception {
            given(taskService.updateTask(eq(99L), any(Task.class), eq(USER_ID)))
                    .willReturn(Optional.empty());

            mockMvc.perform(put("/api/tasks/99")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    task(null, "Ghost", null, Status.DONE, Priority.LOW))))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------
    // DELETE /api/tasks/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/tasks/{id}")
    class DeleteTask {

        @Test
        @DisplayName("returns 204 No Content when task belongs to the user")
        void taskExists_returns204() throws Exception {
            given(taskService.deleteTask(1L, USER_ID)).willReturn(true);

            mockMvc.perform(delete("/api/tasks/1").with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("returns 404 when task does not exist or belongs to another user")
        void taskMissing_returns404() throws Exception {
            given(taskService.deleteTask(99L, USER_ID)).willReturn(false);

            mockMvc.perform(delete("/api/tasks/99").with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
