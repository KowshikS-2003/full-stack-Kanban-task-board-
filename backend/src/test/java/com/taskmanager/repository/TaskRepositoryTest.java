package com.taskmanager.repository;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.Task.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for TaskRepository.
 * @DataJpaTest loads only the JPA layer with an H2 in-memory database.
 * No web layer or full Spring context is loaded.
 */
@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TaskRepository taskRepository;

    // Helper: persist a Task and flush to DB immediately
    private Task persist(String title, String description, Status status) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription(description);
        t.setStatus(status);
        return entityManager.persistFlushFind(t);
    }

    @BeforeEach
    void clearDatabase() {
        taskRepository.deleteAll();
        entityManager.flush();
    }

    // -------------------------------------------------------
    // save / findById
    // -------------------------------------------------------

    @Nested
    @DisplayName("save() and findById()")
    class SaveAndFind {

        @Test
        @DisplayName("persists a task and retrieves it by ID")
        void savePersistsTask() {
            Task task = new Task();
            task.setTitle("Persist me");
            task.setStatus(Status.TODO);

            Task saved = taskRepository.save(task);
            entityManager.flush();

            Optional<Task> found = taskRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("Persist me");
            assertThat(found.get().getStatus()).isEqualTo(Status.TODO);
        }

        @Test
        @DisplayName("@PrePersist sets createdAt automatically")
        void prePersistSetsCreatedAt() {
            Task task = new Task();
            task.setTitle("Timestamped task");
            task.setStatus(Status.TODO);

            Task saved = taskRepository.save(task);
            entityManager.flush();
            entityManager.clear();

            Task reloaded = taskRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("findById returns empty for a non-existent ID")
        void findByIdReturnsEmptyForUnknownId() {
            assertThat(taskRepository.findById(99999L)).isEmpty();
        }

        @Test
        @DisplayName("findAll returns all persisted tasks")
        void findAllReturnsAllTasks() {
            persist("Task 1", null,     Status.TODO);
            persist("Task 2", "detail", Status.DONE);

            List<Task> all = taskRepository.findAll();
            assertThat(all).hasSize(2);
        }
    }

    // -------------------------------------------------------
    // findByStatus (custom query)
    // -------------------------------------------------------

    @Nested
    @DisplayName("findByStatus()")
    class FindByStatus {

        @Test
        @DisplayName("returns only tasks matching the given status")
        void returnsOnlyMatchingStatus() {
            persist("Todo 1",       null, Status.TODO);
            persist("Todo 2",       null, Status.TODO);
            persist("In Progress",  null, Status.IN_PROGRESS);
            persist("Done",         null, Status.DONE);

            List<Task> todos = taskRepository.findByStatus(Status.TODO);

            assertThat(todos).hasSize(2)
                    .allMatch(t -> t.getStatus() == Status.TODO);
        }

        @Test
        @DisplayName("returns empty list when no tasks match the given status")
        void returnsEmptyWhenNoMatch() {
            persist("Only todo", null, Status.TODO);

            assertThat(taskRepository.findByStatus(Status.DONE)).isEmpty();
        }

        @Test
        @DisplayName("returns all three status groups correctly")
        void returnsCorrectCountsForAllStatuses() {
            persist("T1", null, Status.TODO);
            persist("T2", null, Status.TODO);
            persist("I1", null, Status.IN_PROGRESS);
            persist("D1", null, Status.DONE);
            persist("D2", null, Status.DONE);
            persist("D3", null, Status.DONE);

            assertThat(taskRepository.findByStatus(Status.TODO)).hasSize(2);
            assertThat(taskRepository.findByStatus(Status.IN_PROGRESS)).hasSize(1);
            assertThat(taskRepository.findByStatus(Status.DONE)).hasSize(3);
        }
    }

    // -------------------------------------------------------
    // delete
    // -------------------------------------------------------

    @Nested
    @DisplayName("deleteById()")
    class Delete {

        @Test
        @DisplayName("removes the task from the database")
        void deletesTask() {
            Task saved = persist("Delete me", null, Status.TODO);
            Long id = saved.getId();

            taskRepository.deleteById(id);
            entityManager.flush();

            assertThat(taskRepository.findById(id)).isEmpty();
        }

        @Test
        @DisplayName("existsById returns false after deletion")
        void existsByIdReturnsFalseAfterDeletion() {
            Task saved = persist("Temp", null, Status.TODO);
            Long id = saved.getId();

            taskRepository.deleteById(id);
            entityManager.flush();

            assertThat(taskRepository.existsById(id)).isFalse();
        }
    }

    // -------------------------------------------------------
    // update (via save on managed entity)
    // -------------------------------------------------------

    @Nested
    @DisplayName("update via save()")
    class Update {

        @Test
        @DisplayName("updates title and status of an existing task")
        void updatesTitleAndStatus() {
            Task saved = persist("Original title", null, Status.TODO);

            Task toUpdate = taskRepository.findById(saved.getId()).orElseThrow();
            toUpdate.setTitle("Updated title");
            toUpdate.setStatus(Status.IN_PROGRESS);
            taskRepository.save(toUpdate);
            entityManager.flush();
            entityManager.clear();

            Task reloaded = taskRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getTitle()).isEqualTo("Updated title");
            assertThat(reloaded.getStatus()).isEqualTo(Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("count remains the same after an update (no duplicate rows)")
        void countUnchangedAfterUpdate() {
            Task saved = persist("One task", null, Status.TODO);
            long before = taskRepository.count();

            Task toUpdate = taskRepository.findById(saved.getId()).orElseThrow();
            toUpdate.setTitle("Modified");
            taskRepository.save(toUpdate);
            entityManager.flush();

            assertThat(taskRepository.count()).isEqualTo(before);
        }
    }
}
