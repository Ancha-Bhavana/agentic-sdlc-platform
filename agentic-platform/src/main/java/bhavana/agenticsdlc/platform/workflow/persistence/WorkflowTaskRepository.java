package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTaskEntity, WorkflowTaskEntity.Key> {
    List<WorkflowTaskEntity> findByWorkflowIdAndWorkflowRevisionOrderByTaskId(UUID workflowId, int workflowRevision);
    Page<WorkflowTaskEntity> findByWorkflowIdAndWorkflowRevision(UUID workflowId, int workflowRevision, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from WorkflowTaskEntity task where task.workflowId=:workflowId and task.workflowRevision=:revision and task.taskId=:taskId")
    Optional<WorkflowTaskEntity> lockById(@Param("workflowId") UUID workflowId,
                                         @Param("revision") int revision,
                                         @Param("taskId") String taskId);
}
