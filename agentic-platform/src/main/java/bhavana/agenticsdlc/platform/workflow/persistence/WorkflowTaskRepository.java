package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTaskEntity, WorkflowTaskEntity.Key> {
    List<WorkflowTaskEntity> findByWorkflowIdAndWorkflowRevisionOrderByTaskId(UUID workflowId, int workflowRevision);
}
