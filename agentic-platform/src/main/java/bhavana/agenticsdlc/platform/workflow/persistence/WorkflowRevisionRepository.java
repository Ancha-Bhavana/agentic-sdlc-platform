package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.*;

public interface WorkflowRevisionRepository extends JpaRepository<WorkflowRevisionEntity, WorkflowRevisionEntity.Key> {
    List<WorkflowRevisionEntity> findByWorkflowIdOrderByRevision(UUID workflowId);
    Page<WorkflowRevisionEntity> findByWorkflowId(UUID workflowId, Pageable pageable);
}
