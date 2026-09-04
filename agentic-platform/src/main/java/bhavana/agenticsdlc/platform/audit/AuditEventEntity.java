package bhavana.agenticsdlc.platform.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity {
    @Id private UUID id;
    private UUID workflowId;
    private Integer workflowRevision;
    @Column(nullable = false, length = 80) private String correlationId;
    @Column(nullable = false, length = 80) private String eventType;
    @Column(nullable = false, length = 200) private String actor;
    @Column(nullable = false, length = 80) private String actorRole;
    @Column(nullable = false, length = 64) private String payloadHash;
    @Column(nullable = false, length = 2000) private String details;
    @Column(nullable = false) private Instant createdAt;
    protected AuditEventEntity() { }
    public AuditEventEntity(UUID workflowId, Integer revision, String correlationId, String eventType,
                            String actor, String actorRole, String payloadHash, String details, Instant createdAt) {
        this.id = UUID.randomUUID(); this.workflowId = workflowId; this.workflowRevision = revision;
        this.correlationId = correlationId; this.eventType = eventType; this.actor = actor;
        this.actorRole = actorRole; this.payloadHash = payloadHash; this.details = details; this.createdAt = createdAt;
    }
    public UUID getId() { return id; } public UUID getWorkflowId() { return workflowId; }
    public Integer getWorkflowRevision() { return workflowRevision; } public String getCorrelationId() { return correlationId; }
    public String getEventType() { return eventType; } public String getActor() { return actor; }
    public String getActorRole() { return actorRole; } public String getPayloadHash() { return payloadHash; }
    public String getDetails() { return details; } public Instant getCreatedAt() { return createdAt; }
}
