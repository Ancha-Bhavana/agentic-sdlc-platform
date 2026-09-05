package bhavana.agenticsdlc.platform.workflow.persistence;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface ContextArtifactRepository extends JpaRepository<ContextArtifactEntity, UUID> {
    Page<ContextArtifactEntity> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId, Pageable pageable);
    Optional<ContextArtifactEntity> findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
            UUID workflowId, int workflowRevision, String artifactKey);
    long countByWorkflowIdAndArtifactKey(UUID workflowId, String artifactKey);
    List<ContextArtifactEntity> findByWorkflowIdAndWorkflowRevisionOrderByCreatedAtAsc(
            UUID workflowId, int workflowRevision);
}
