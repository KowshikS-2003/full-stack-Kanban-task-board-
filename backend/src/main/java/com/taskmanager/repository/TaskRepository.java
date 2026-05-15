package com.taskmanager.repository;

import com.taskmanager.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // User-scoped queries (used by TaskService)
    List<Task>     findByUserId(Long userId);
    Optional<Task> findByIdAndUserId(Long id, Long userId);

    // Status / priority queries (used by TaskRepositoryTest)
    List<Task> findByStatus(Task.Status status);
    List<Task> findByPriority(Task.Priority priority);
    List<Task> findByStatusAndPriority(Task.Status status, Task.Priority priority);
}
