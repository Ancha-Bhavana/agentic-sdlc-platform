package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ContextArtifactRepository extends JpaRepository<ContextArtifactEntity, UUID> {
    Page<ContextArtifactEntity> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId, Pageable pageable);
}
