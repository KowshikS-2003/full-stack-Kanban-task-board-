package com.taskmanager.service;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.Task.Priority;
import com.taskmanager.entity.Task.Status;
import com.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private Task savedTask(Long id, String title, Status status) {
        return savedTask(id, title, status, Priority.MEDIUM);
    }

    private Task savedTask(Long id, String title, Status status, Priority priority) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setStatus(status);
        t.setPriority(priority);
        t.setUserId(USER_ID);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    // -------------------------------------------------------
    // getAllTasks
    // -------------------------------------------------------

    @Nested
    @DisplayName("getAllTasks()")
    class GetAllTasks {

        @Test
        @DisplayName("returns only tasks belonging to the given user")
        void returnsAllTasks() {
            List<Task> expected = List.of(
                    savedTask(1L, "Task A", Status.TODO,        Priority.LOW),
                    savedTask(2L, "Task B", Status.IN_PROGRESS, Priority.HIGH)
            );
            given(taskRepository.findByUserId(USER_ID)).willReturn(expected);

            List<Task> result = taskService.getAllTasks(USER_ID);

            assertThat(result).hasSize(2).containsExactlyElementsOf(expected);
            then(taskRepository).should().findByUserId(USER_ID);
        }

        @Test
        @DisplayName("returns empty list when user has no tasks")
        void returnsEmptyList() {
            given(taskRepository.findByUserId(USER_ID)).willReturn(List.of());
            assertThat(taskService.getAllTasks(USER_ID)).isEmpty();
        }
    }

    // -------------------------------------------------------
    // getTaskById
    // -------------------------------------------------------

    @Nested
    @DisplayName("getTaskById()")
    class GetTaskById {

        @Test
        @DisplayName("returns the task when it exists and belongs to the user")
        void taskExists_returnsTask() {
            Task task = savedTask(1L, "Existing Task", Status.TODO, Priority.MEDIUM);
            given(taskRepository.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(task));

            Optional<Task> result = taskService.getTaskById(1L, USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getPriority()).isEqualTo(Priority.MEDIUM);
        }

        @Test
        @DisplayName("returns empty when task does not exist or belongs to another user")
        void taskMissing_returnsEmpty() {
            given(taskRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());
            assertThat(taskService.getTaskById(99L, USER_ID)).isEmpty();
        }
    }

    // -------------------------------------------------------
    // createTask
    // -------------------------------------------------------

    @Nested
    @DisplayName("createTask()")
    class CreateTask {

        @Test
        @DisplayName("sets userId on the task and saves it")
        void savesWithUserId() {
            Task input = new Task();
            input.setTitle("New Task");
            input.setStatus(Status.TODO);

            Task persisted = savedTask(1L, "New Task", Status.TODO, Priority.MEDIUM);
            given(taskRepository.save(input)).willReturn(persisted);

            Task result = taskService.createTask(input, USER_ID);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            then(taskRepository).should().save(input);
        }

        @Test
        @DisplayName("saves task with HIGH priority when explicitly set")
        void savesWithHighPriority() {
            Task input = new Task();
            input.setTitle("Urgent Task");
            input.setPriority(Priority.HIGH);

            Task persisted = savedTask(2L, "Urgent Task", Status.TODO, Priority.HIGH);
            given(taskRepository.save(input)).willReturn(persisted);

            assertThat(taskService.createTask(input, USER_ID).getPriority()).isEqualTo(Priority.HIGH);
        }
    }

    // -------------------------------------------------------
    // updateTask
    // -------------------------------------------------------

    @Nested
    @DisplayName("updateTask()")
    class UpdateTask {

        @Test
        @DisplayName("updates all mutable fields including priority")
        void taskExists_updatesFields() {
            Task existing = savedTask(1L, "Old Title", Status.TODO, Priority.LOW);
            given(taskRepository.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(existing));

            Task update = new Task();
            update.setTitle("New Title");
            update.setDescription("Updated description");
            update.setStatus(Status.IN_PROGRESS);
            update.setPriority(Priority.HIGH);

            Task afterSave = savedTask(1L, "New Title", Status.IN_PROGRESS, Priority.HIGH);
            afterSave.setDescription("Updated description");
            given(taskRepository.save(existing)).willReturn(afterSave);

            Optional<Task> result = taskService.updateTask(1L, update, USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getTitle()).isEqualTo("New Title");
            assertThat(result.get().getDescription()).isEqualTo("Updated description");
            assertThat(result.get().getStatus()).isEqualTo(Status.IN_PROGRESS);
            assertThat(result.get().getPriority()).isEqualTo(Priority.HIGH);
            then(taskRepository).should().save(existing);
        }

        @Test
        @DisplayName("updates priority from MEDIUM to HIGH")
        void updatesPriority() {
            Task existing = savedTask(1L, "Task", Status.TODO, Priority.MEDIUM);
            given(taskRepository.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(existing));

            Task update = new Task();
            update.setTitle("Task");
            update.setStatus(Status.TODO);
            update.setPriority(Priority.HIGH);

            Task afterSave = savedTask(1L, "Task", Status.TODO, Priority.HIGH);
            given(taskRepository.save(existing)).willReturn(afterSave);

            assertThat(taskService.updateTask(1L, update, USER_ID).get().getPriority())
                    .isEqualTo(Priority.HIGH);
        }

        @Test
        @DisplayName("returns empty when task does not exist or belongs to another user")
        void taskMissing_returnsEmpty() {
            given(taskRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());

            Task update = new Task();
            update.setTitle("Ghost");
            update.setStatus(Status.DONE);
            update.setPriority(Priority.LOW);

            assertThat(taskService.updateTask(99L, update, USER_ID)).isEmpty();
            then(taskRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("advances status from TODO → IN_PROGRESS")
        void advancesStatus() {
            Task existing = savedTask(2L, "Sprint task", Status.TODO, Priority.MEDIUM);
            given(taskRepository.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.of(existing));

            Task update = new Task();
            update.setTitle("Sprint task");
            update.setStatus(Status.IN_PROGRESS);
            update.setPriority(Priority.MEDIUM);

            Task afterSave = savedTask(2L, "Sprint task", Status.IN_PROGRESS, Priority.MEDIUM);
            given(taskRepository.save(existing)).willReturn(afterSave);

            assertThat(taskService.updateTask(2L, update, USER_ID).get().getStatus())
                    .isEqualTo(Status.IN_PROGRESS);
        }
    }

    // -------------------------------------------------------
    // deleteTask
    // -------------------------------------------------------

    @Nested
    @DisplayName("deleteTask()")
    class DeleteTask {

        @Test
        @DisplayName("deletes task and returns true when it belongs to the user")
        void taskExists_deletesAndReturnsTrue() {
            Task task = savedTask(1L, "Delete me", Status.TODO);
            given(taskRepository.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(task));

            assertThat(taskService.deleteTask(1L, USER_ID)).isTrue();
            then(taskRepository).should().delete(task);
        }

        @Test
        @DisplayName("returns false when task does not exist or belongs to another user")
        void taskMissing_returnsFalse() {
            given(taskRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());

            assertThat(taskService.deleteTask(99L, USER_ID)).isFalse();
            then(taskRepository).should(never()).delete(any(Task.class));
        }
    }
}
