package com.taskmanager.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.taskmanager.entity.Task;
import com.taskmanager.repository.TaskRepository;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> getAllTasks(Long userId) {
        return taskRepository.findByUserId(userId);
    }

    public Optional<Task> getTaskById(Long id, Long userId) {
        return taskRepository.findByIdAndUserId(id, userId);
    }

    public Task createTask(Task task, Long userId) {
        task.setUserId(userId);
        return taskRepository.save(task);
    }

    public Optional<Task> updateTask(Long id, Task updatedTask, Long userId) {
        return taskRepository.findByIdAndUserId(id, userId).map(existing -> {
            existing.setTitle(updatedTask.getTitle());
            existing.setDescription(updatedTask.getDescription());
            existing.setStatus(updatedTask.getStatus());
            existing.setPriority(updatedTask.getPriority());
            return taskRepository.save(existing);
        });
    }

    public boolean deleteTask(Long id, Long userId) {
        return taskRepository.findByIdAndUserId(id, userId)
                .map(task -> { taskRepository.delete(task); return true; })
                .orElse(false);
    }
}

