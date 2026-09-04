package bhavana.agenticsdlc.platform.governance;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy_result")
public class PolicyResultEntity {
    @Id private UUID id;
    private UUID workflowId;
    @Column(nullable = false) private int workflowRevision;
    @Column(nullable = false, length = 100) private String policyName;
    @Column(nullable = false) private boolean allowed;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false) private Instant evaluatedAt;
    protected PolicyResultEntity() { }
    public PolicyResultEntity(UUID workflowId, int revision, String policyName, boolean allowed,
                              String reason, Instant evaluatedAt) {
        this.id = UUID.randomUUID(); this.workflowId = workflowId; this.workflowRevision = revision;
        this.policyName = policyName; this.allowed = allowed; this.reason = reason; this.evaluatedAt = evaluatedAt;
    }
    public UUID getId() { return id; } public UUID getWorkflowId() { return workflowId; }
    public int getWorkflowRevision() { return workflowRevision; } public String getPolicyName() { return policyName; }
    public boolean isAllowed() { return allowed; } public String getReason() { return reason; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
