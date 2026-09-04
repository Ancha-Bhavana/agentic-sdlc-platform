package bhavana.agenticsdlc.platform.governance;

import bhavana.agenticsdlc.platform.workflow.domain.GateType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, UUID> {
    List<ApprovalEntity> findByWorkflowIdAndValidTrue(UUID workflowId);
    Optional<ApprovalEntity> findFirstByWorkflowIdAndWorkflowRevisionAndGateTypeAndValidTrueOrderByDecidedAtDesc(
            UUID workflowId, int revision, GateType gateType);
}
