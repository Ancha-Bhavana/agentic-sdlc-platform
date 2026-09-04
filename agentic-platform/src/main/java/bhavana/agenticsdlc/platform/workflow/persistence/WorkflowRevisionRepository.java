package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRevisionRepository extends JpaRepository<WorkflowRevisionEntity, WorkflowRevisionEntity.Key> {
}
