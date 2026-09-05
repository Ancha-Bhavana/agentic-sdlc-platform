package bhavana.agenticsdlc.platform.scenario;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_execution_spec")
@IdClass(WorkflowExecutionSpecEntity.Key.class)
public class WorkflowExecutionSpecEntity {
    @Id private UUID workflowId;
    @Id private int workflowRevision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ScenarioType scenarioType;
    @Column(nullable = false, columnDefinition = "text") private String requirementText;
    @Column(nullable = false, columnDefinition = "text") private String repositoryPath;
    @Column(nullable = false, length = 80) private String correlationId;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected WorkflowExecutionSpecEntity() {}

    public WorkflowExecutionSpecEntity(UUID workflowId, int workflowRevision, ScenarioType scenarioType,
                                       String requirementText, String repositoryPath,
                                       String correlationId, Instant createdAt) {
        if (workflowId == null || workflowRevision < 1 || scenarioType == null || requirementText == null
                || requirementText.isBlank() || repositoryPath == null || repositoryPath.isBlank()
                || correlationId == null || correlationId.isBlank() || createdAt == null)
            throw new IllegalArgumentException("Complete workflow execution specification required");
        this.workflowId = workflowId; this.workflowRevision = workflowRevision; this.scenarioType = scenarioType;
        this.requirementText = requirementText; this.repositoryPath = repositoryPath;
        this.correlationId = correlationId; this.createdAt = createdAt;
    }

    public UUID getWorkflowId() { return workflowId; }
    public int getWorkflowRevision() { return workflowRevision; }
    public ScenarioType getScenarioType() { return scenarioType; }
    public String getRequirementText() { return requirementText; }
    public String getRepositoryPath() { return repositoryPath; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public record Key(UUID workflowId, int workflowRevision) implements Serializable {}
}
