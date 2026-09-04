package bhavana.agenticsdlc.platform.governance;

import bhavana.agenticsdlc.platform.workflow.domain.GateType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_decision")
public class ApprovalEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID workflowId;
    @Column(nullable = false) private int workflowRevision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private GateType gateType;
    @Column(nullable = false, length = 64) private String artifactHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ApprovalDecision decision;
    @Column(nullable = false, length = 200) private String actor;
    @Column(nullable = false, length = 80) private String actorRole;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false) private boolean valid;
    @Column(nullable = false) private Instant decidedAt;

    protected ApprovalEntity() { }

    public ApprovalEntity(UUID workflowId, int revision, GateType gateType, String artifactHash,
                          ApprovalDecision decision, String actor, String actorRole,
                          String reason, Instant decidedAt) {
        if (workflowId == null || revision < 1 || gateType == null || artifactHash == null
                || !artifactHash.matches("[0-9a-f]{64}") || decision == null || isBlank(actor)
                || isBlank(actorRole) || isBlank(reason) || reason.length() > 1000 || decidedAt == null)
            throw new IllegalArgumentException("Complete approval evidence is required");
        this.id = UUID.randomUUID(); this.workflowId = workflowId; this.workflowRevision = revision;
        this.gateType = gateType; this.artifactHash = artifactHash; this.decision = decision;
        this.actor = actor; this.actorRole = actorRole; this.reason = reason;
        this.decidedAt = decidedAt; this.valid = true;
    }

    public void invalidate() { valid = false; }
    public UUID getId() { return id; }
    public UUID getWorkflowId() { return workflowId; }
    public int getWorkflowRevision() { return workflowRevision; }
    public GateType getGateType() { return gateType; }
    public String getArtifactHash() { return artifactHash; }
    public ApprovalDecision getDecision() { return decision; }
    public String getActor() { return actor; }
    public String getActorRole() { return actorRole; }
    public String getReason() { return reason; }
    public boolean isValid() { return valid; }
    public Instant getDecidedAt() { return decidedAt; }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
