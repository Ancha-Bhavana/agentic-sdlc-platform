package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, UUID> {
    Optional<WorkflowRunEntity> findByCorrelationId(String correlationId);
    List<WorkflowRunEntity> findByStatusIn(Collection<WorkflowStatus> statuses);
}
