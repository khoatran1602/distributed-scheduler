package com.demo.scheduler.repository;

import com.demo.scheduler.model.Task;
import com.demo.scheduler.model.Task.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Repository for Task entity persistence operations.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status);

    long countByStatus(TaskStatus status);

    @Query("SELECT t.status as status, COUNT(t) as count FROM Task t GROUP BY t.status")
    List<Object[]> getStatusCounts();

    List<Task> findTop20ByOrderByCreatedAtDesc();
}
