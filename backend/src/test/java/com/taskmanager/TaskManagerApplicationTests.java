package com.taskmanager;

import com.taskmanager.entity.Task;
import com.taskmanager.service.TaskService;
import com.taskmanager.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TaskManagerApplicationTests {

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserService userService;

    private Long testUserId;

    @BeforeEach
    void setUpUser() {
        // Register a unique user per test so tasks are isolated
        var user = userService.register("user_" + System.nanoTime(), "password");
        testUserId = user.getId();
    }

    @Test
    void contextLoads() {
        assertThat(taskService).isNotNull();
        assertThat(userService).isNotNull();
    }

    @Test
    void createAndRetrieveTask() {
        Task task = new Task();
        task.setTitle("Write unit tests");
        task.setDescription("Cover the service layer");
        task.setStatus(Task.Status.TODO);
        task.setPriority(Task.Priority.HIGH);

        Task saved = taskService.createTask(task, testUserId);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPriority()).isEqualTo(Task.Priority.HIGH);
        assertThat(saved.getUserId()).isEqualTo(testUserId);

        Optional<Task> found = taskService.getTaskById(saved.getId(), testUserId);
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Write unit tests");
        assertThat(found.get().getPriority()).isEqualTo(Task.Priority.HIGH);
    }

    @Test
    void getAllTasksReturnsList() {
        Task t1 = new Task();
        t1.setTitle("Task A");
        t1.setStatus(Task.Status.TODO);
        taskService.createTask(t1, testUserId);

        List<Task> tasks = taskService.getAllTasks(testUserId);
        assertThat(tasks).isNotEmpty();
    }

    @Test
    void updateTaskStatus() {
        Task task = new Task();
        task.setTitle("Move me forward");
        task.setStatus(Task.Status.TODO);
        task.setPriority(Task.Priority.LOW);
        Task saved = taskService.createTask(task, testUserId);

        Task patch = new Task();
        patch.setTitle(saved.getTitle());
        patch.setDescription(saved.getDescription());
        patch.setStatus(Task.Status.IN_PROGRESS);
        patch.setPriority(Task.Priority.HIGH);

        Optional<Task> updated = taskService.updateTask(saved.getId(), patch, testUserId);

        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(Task.Status.IN_PROGRESS);
        assertThat(updated.get().getPriority()).isEqualTo(Task.Priority.HIGH);
    }

    @Test
    void deleteTask() {
        Task task = new Task();
        task.setTitle("Temporary task");
        task.setStatus(Task.Status.DONE);
        Task saved = taskService.createTask(task, testUserId);

        boolean deleted = taskService.deleteTask(saved.getId(), testUserId);

        assertThat(deleted).isTrue();
        assertThat(taskService.getTaskById(saved.getId(), testUserId)).isEmpty();
    }

    @Test
    void deleteNonExistentTaskReturnsFalse() {
        boolean result = taskService.deleteTask(99999L, testUserId);
        assertThat(result).isFalse();
    }

    @Test
    void tasksAreIsolatedBetweenUsers() {
        // Create a second user
        var user2 = userService.register("user_b_" + System.nanoTime(), "password");
        Long userId2 = user2.getId();

        Task task = new Task();
        task.setTitle("User A's secret task");
        task.setStatus(Task.Status.TODO);
        Task saved = taskService.createTask(task, testUserId);

        // User B cannot see User A's task
        assertThat(taskService.getTaskById(saved.getId(), userId2)).isEmpty();
        assertThat(taskService.getAllTasks(userId2)).isEmpty();
    }
}
