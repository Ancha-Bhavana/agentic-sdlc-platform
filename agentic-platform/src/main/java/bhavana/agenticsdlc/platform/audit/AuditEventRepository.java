package bhavana.agenticsdlc.platform.audit;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    Page<AuditEventEntity> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId, Pageable pageable);
}
