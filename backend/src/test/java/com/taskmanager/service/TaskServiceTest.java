package com.taskmanager.service;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.Task.Status;
import com.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Pure unit tests for TaskService.
 * No Spring context is loaded — the repository is mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    // Helper: create a Task with a preset ID (simulates a saved entity)
    private Task savedTask(Long id, String title, Status status) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setStatus(status);
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
        @DisplayName("returns all tasks from the repository")
        void returnsAllTasks() {
            List<Task> expected = List.of(
                    savedTask(1L, "Task A", Status.TODO),
                    savedTask(2L, "Task B", Status.IN_PROGRESS)
            );
            given(taskRepository.findAll()).willReturn(expected);

            List<Task> result = taskService.getAllTasks();

            assertThat(result).hasSize(2).containsExactlyElementsOf(expected);
            then(taskRepository).should().findAll();
        }

        @Test
        @DisplayName("returns empty list when repository is empty")
        void returnsEmptyList() {
            given(taskRepository.findAll()).willReturn(List.of());

            assertThat(taskService.getAllTasks()).isEmpty();
        }
    }

    // -------------------------------------------------------
    // getTaskById
    // -------------------------------------------------------

    @Nested
    @DisplayName("getTaskById()")
    class GetTaskById {

        @Test
        @DisplayName("returns the task when it exists")
        void taskExists_returnsTask() {
            Task task = savedTask(1L, "Existing Task", Status.TODO);
            given(taskRepository.findById(1L)).willReturn(Optional.of(task));

            Optional<Task> result = taskService.getTaskById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
            assertThat(result.get().getTitle()).isEqualTo("Existing Task");
        }

        @Test
        @DisplayName("returns empty Optional when task does not exist")
        void taskMissing_returnsEmpty() {
            given(taskRepository.findById(99L)).willReturn(Optional.empty());

            assertThat(taskService.getTaskById(99L)).isEmpty();
        }
    }

    // -------------------------------------------------------
    // createTask
    // -------------------------------------------------------

    @Nested
    @DisplayName("createTask()")
    class CreateTask {

        @Test
        @DisplayName("saves and returns the created task")
        void savesAndReturnsTask() {
            Task input = new Task();
            input.setTitle("New Task");
            input.setStatus(Status.TODO);

            Task persisted = savedTask(1L, "New Task", Status.TODO);
            given(taskRepository.save(input)).willReturn(persisted);

            Task result = taskService.createTask(input);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("New Task");
            then(taskRepository).should().save(input);
        }

        @Test
        @DisplayName("defaults status to TODO when not specified")
        void defaultStatusIsTodo() {
            Task input = new Task();
            input.setTitle("No-status task");
            // status defaults to TODO in the entity

            Task persisted = savedTask(5L, "No-status task", Status.TODO);
            given(taskRepository.save(input)).willReturn(persisted);

            Task result = taskService.createTask(input);

            assertThat(result.getStatus()).isEqualTo(Status.TODO);
        }
    }

    // -------------------------------------------------------
    // updateTask
    // -------------------------------------------------------

    @Nested
    @DisplayName("updateTask()")
    class UpdateTask {

        @Test
        @DisplayName("updates all mutable fields and returns the updated task")
        void taskExists_updatesFields() {
            Task existing = savedTask(1L, "Old Title", Status.TODO);
            given(taskRepository.findById(1L)).willReturn(Optional.of(existing));

            Task update = new Task();
            update.setTitle("New Title");
            update.setDescription("Updated description");
            update.setStatus(Status.IN_PROGRESS);

            Task afterSave = savedTask(1L, "New Title", Status.IN_PROGRESS);
            afterSave.setDescription("Updated description");
            given(taskRepository.save(existing)).willReturn(afterSave);

            Optional<Task> result = taskService.updateTask(1L, update);

            assertThat(result).isPresent();
            assertThat(result.get().getTitle()).isEqualTo("New Title");
            assertThat(result.get().getDescription()).isEqualTo("Updated description");
            assertThat(result.get().getStatus()).isEqualTo(Status.IN_PROGRESS);
            then(taskRepository).should().save(existing);
        }

        @Test
        @DisplayName("returns empty Optional when task does not exist")
        void taskMissing_returnsEmpty() {
            given(taskRepository.findById(99L)).willReturn(Optional.empty());

            Task update = new Task();
            update.setTitle("Ghost update");
            update.setStatus(Status.DONE);

            Optional<Task> result = taskService.updateTask(99L, update);

            assertThat(result).isEmpty();
            then(taskRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("advances status from TODO → IN_PROGRESS")
        void advancesStatusTodoToInProgress() {
            Task existing = savedTask(2L, "Sprint task", Status.TODO);
            given(taskRepository.findById(2L)).willReturn(Optional.of(existing));

            Task update = new Task();
            update.setTitle("Sprint task");
            update.setStatus(Status.IN_PROGRESS);

            Task afterSave = savedTask(2L, "Sprint task", Status.IN_PROGRESS);
            given(taskRepository.save(existing)).willReturn(afterSave);

            Optional<Task> result = taskService.updateTask(2L, update);

            assertThat(result.get().getStatus()).isEqualTo(Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("advances status from IN_PROGRESS → DONE")
        void advancesStatusInProgressToDone() {
            Task existing = savedTask(3L, "Almost done", Status.IN_PROGRESS);
            given(taskRepository.findById(3L)).willReturn(Optional.of(existing));

            Task update = new Task();
            update.setTitle("Almost done");
            update.setStatus(Status.DONE);

            Task afterSave = savedTask(3L, "Almost done", Status.DONE);
            given(taskRepository.save(existing)).willReturn(afterSave);

            Optional<Task> result = taskService.updateTask(3L, update);

            assertThat(result.get().getStatus()).isEqualTo(Status.DONE);
        }
    }

    // -------------------------------------------------------
    // deleteTask
    // -------------------------------------------------------

    @Nested
    @DisplayName("deleteTask()")
    class DeleteTask {

        @Test
        @DisplayName("deletes task and returns true when it exists")
        void taskExists_deletesAndReturnsTrue() {
            given(taskRepository.existsById(1L)).willReturn(true);

            boolean result = taskService.deleteTask(1L);

            assertThat(result).isTrue();
            then(taskRepository).should().deleteById(1L);
        }

        @Test
        @DisplayName("returns false and does not call deleteById when task does not exist")
        void taskMissing_returnsFalse() {
            given(taskRepository.existsById(99L)).willReturn(false);

            boolean result = taskService.deleteTask(99L);

            assertThat(result).isFalse();
            then(taskRepository).should(never()).deleteById(any());
        }
    }
}
