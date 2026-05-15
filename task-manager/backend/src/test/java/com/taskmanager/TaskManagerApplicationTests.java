package com.taskmanager;

import com.taskmanager.entity.Task;
import com.taskmanager.service.TaskService;
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

    @Test
    void contextLoads() {
        assertThat(taskService).isNotNull();
    }

    @Test
    void createAndRetrieveTask() {
        Task task = new Task();
        task.setTitle("Write unit tests");
        task.setDescription("Cover the service layer");
        task.setStatus(Task.Status.TODO);

        Task saved = taskService.createTask(task);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Task> found = taskService.getTaskById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Write unit tests");
    }

    @Test
    void getAllTasksReturnsList() {
        Task t1 = new Task();
        t1.setTitle("Task A");
        t1.setStatus(Task.Status.TODO);
        taskService.createTask(t1);

        List<Task> tasks = taskService.getAllTasks();
        assertThat(tasks).isNotEmpty();
    }

    @Test
    void updateTaskStatus() {
        Task task = new Task();
        task.setTitle("Move me forward");
        task.setStatus(Task.Status.TODO);
        Task saved = taskService.createTask(task);

        Task patch = new Task();
        patch.setTitle(saved.getTitle());
        patch.setDescription(saved.getDescription());
        patch.setStatus(Task.Status.IN_PROGRESS);

        Optional<Task> updated = taskService.updateTask(saved.getId(), patch);

        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(Task.Status.IN_PROGRESS);
    }

    @Test
    void deleteTask() {
        Task task = new Task();
        task.setTitle("Temporary task");
        task.setStatus(Task.Status.DONE);
        Task saved = taskService.createTask(task);

        boolean deleted = taskService.deleteTask(saved.getId());

        assertThat(deleted).isTrue();
        assertThat(taskService.getTaskById(saved.getId())).isEmpty();
    }

    @Test
    void deleteNonExistentTaskReturnsFalse() {
        boolean result = taskService.deleteTask(99999L);
        assertThat(result).isFalse();
    }
}
