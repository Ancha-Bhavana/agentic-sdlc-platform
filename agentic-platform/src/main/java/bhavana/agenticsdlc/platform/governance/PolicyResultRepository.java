package bhavana.agenticsdlc.platform.governance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PolicyResultRepository extends JpaRepository<PolicyResultEntity, UUID> {
    Page<PolicyResultEntity> findByWorkflowId(UUID workflowId, Pageable pageable);
}
