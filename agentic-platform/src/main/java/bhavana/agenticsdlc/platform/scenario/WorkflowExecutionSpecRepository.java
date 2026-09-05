package bhavana.agenticsdlc.platform.scenario;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowExecutionSpecRepository extends JpaRepository<WorkflowExecutionSpecEntity, WorkflowExecutionSpecEntity.Key> {}
