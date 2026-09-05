package bhavana.agenticsdlc.platform.api;

import bhavana.agenticsdlc.platform.audit.*;
import bhavana.agenticsdlc.platform.governance.*;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/workflows/{workflowId}")
public class EvidenceController {
    private final WorkflowRevisionRepository revisions;
    private final ContextArtifactRepository artifacts;
    private final AuditEventRepository audit;
    private final PolicyResultRepository policies;
    public EvidenceController(WorkflowRevisionRepository revisions, ContextArtifactRepository artifacts,
                              AuditEventRepository audit, PolicyResultRepository policies) {
        this.revisions = revisions; this.artifacts = artifacts; this.audit = audit; this.policies = policies;
    }

    @GetMapping("/revisions")
    @Operation(summary = "List workflow revisions")
    public Page<RevisionView> revisions(@PathVariable UUID workflowId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return revisions.findByWorkflowId(workflowId, PageRequest.of(page, size, Sort.by("revision")))
                .map(RevisionView::from);
    }

    @GetMapping("/artifacts")
    @Operation(summary = "List bounded artifact metadata without exposing unrestricted content")
    public Page<ArtifactView> artifacts(@PathVariable UUID workflowId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return artifacts.findByWorkflowIdOrderByCreatedAtDesc(workflowId, PageRequest.of(page, size))
                .map(ArtifactView::from);
    }

    @GetMapping("/artifacts/{artifactId}")
    @Operation(summary = "Read one bounded workflow artifact")
    public ArtifactContentView artifact(@PathVariable UUID workflowId, @PathVariable UUID artifactId) {
        ContextArtifactEntity value = artifacts.findById(artifactId)
                .filter(candidate -> candidate.getWorkflowId().equals(workflowId))
                .orElseThrow(() -> new NoSuchElementException("Workflow artifact not found"));
        String content = value.getContentJson();
        if (content.length() > 128_000) content = content.substring(0, 128_000);
        return new ArtifactContentView(ArtifactView.from(value), content);
    }

    @GetMapping("/audit-events")
    @Operation(summary = "List redacted audit events")
    public Page<AuditView> audit(@PathVariable UUID workflowId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return audit.findByWorkflowIdOrderByCreatedAtDesc(workflowId, PageRequest.of(page, size))
                .map(AuditView::from);
    }

    @GetMapping("/policy-results")
    @Operation(summary = "List policy decisions")
    public Page<PolicyView> policies(@PathVariable UUID workflowId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return policies.findByWorkflowId(workflowId, PageRequest.of(page, size, Sort.by("evaluatedAt").descending()))
                .map(PolicyView::from);
    }

    public record RevisionView(int revision, String requirementHash, String repositoryHash, Instant createdAt) {
        static RevisionView from(WorkflowRevisionEntity value) {
            return new RevisionView(value.getRevision(), value.getRequirementHash(), value.getRepositoryHash(), value.getCreatedAt());
        }
    }
    public record ArtifactView(UUID id, int revision, String key, long version, String producer,
                               String schemaVersion, String contentHash, Instant createdAt) {
        static ArtifactView from(ContextArtifactEntity value) {
            return new ArtifactView(value.getId(), value.getWorkflowRevision(), value.getArtifactKey(),
                    value.getArtifactVersion(), value.getProducerTaskId(), value.getSchemaVersion(),
                    value.getContentHash(), value.getCreatedAt());
        }
    }
    public record ArtifactContentView(ArtifactView metadata, String content) { }
    public record AuditView(UUID id, Integer revision, String eventType, String actor, String role,
                            String payloadHash, String details, Instant createdAt) {
        static AuditView from(AuditEventEntity value) {
            return new AuditView(value.getId(), value.getWorkflowRevision(), value.getEventType(),
                    value.getActor(), value.getActorRole(), value.getPayloadHash(), value.getDetails(), value.getCreatedAt());
        }
    }
    public record PolicyView(UUID id, int revision, String policy, boolean allowed, String reason, Instant evaluatedAt) {
        static PolicyView from(PolicyResultEntity value) {
            return new PolicyView(value.getId(), value.getWorkflowRevision(), value.getPolicyName(),
                    value.isAllowed(), value.getReason(), value.getEvaluatedAt());
        }
    }
}
