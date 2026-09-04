package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.*;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTaskEntity, WorkflowTaskEntity.Key> {
    List<WorkflowTaskEntity> findByWorkflowIdAndWorkflowRevisionOrderByTaskId(UUID workflowId, int workflowRevision);
    Page<WorkflowTaskEntity> findByWorkflowIdAndWorkflowRevision(UUID workflowId, int workflowRevision, Pageable pageable);
}
