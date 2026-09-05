package bhavana.agenticsdlc.platform.governance;

import bhavana.agenticsdlc.platform.audit.AuditService;
import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import bhavana.agenticsdlc.platform.repository.FileHashService;
import bhavana.agenticsdlc.platform.workflow.domain.*;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import bhavana.agenticsdlc.platform.scenario.DeterministicScenarioExecutor;
import bhavana.agenticsdlc.platform.observability.WorkflowMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

@Service
public class ApprovalService {
    private final ApprovalRepository approvals;
    private final WorkflowRunRepository runs;
    private final AuditService audit;
    private final Clock clock;
    private final FileHashService hashes = new FileHashService();
    private final DeterministicScenarioExecutor scenarios;
    private final WorkflowMetrics metrics;
    private final ContextArtifactRepository artifacts;
    public ApprovalService(ApprovalRepository approvals, WorkflowRunRepository runs,
                           AuditService audit, Clock clock, DeterministicScenarioExecutor scenarios,
                           WorkflowMetrics metrics, ContextArtifactRepository artifacts) {
        this.approvals = approvals; this.runs = runs; this.audit = audit; this.clock = clock;
        this.scenarios = scenarios;
        this.metrics = metrics;
        this.artifacts = artifacts;
    }

    @Transactional
    public ApprovalEntity decide(UUID workflowId, int revision, GateType gate,
                                 ApprovalDecision decision, Map<String, String> artifactHashes,
                                 String reason, ActorIdentity actor, String correlationId) {
        WorkflowRunEntity run = runs.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        if (run.getStatus() != WorkflowStatus.AWAITING_APPROVAL || run.getCurrentRevision() != revision)
            throw new IllegalStateException("Approval targets a stale revision or workflow not awaiting approval");
        requireRole(gate, actor.role());
        requireGateEvidence(gate, artifactHashes);
        verifyCurrentArtifacts(workflowId, revision, artifactHashes);
        String reviewedHash = artifactSetHash(artifactHashes);
        ApprovalEntity approval = approvals.save(new ApprovalEntity(workflowId, revision, gate,
                reviewedHash, decision, actor.name(), actor.role(), reason, clock.instant()));
        if (decision == ApprovalDecision.REJECTED) {
            run.transitionTo(WorkflowStatus.REJECTED, clock.instant());
            metrics.outcome("rejected", java.time.Duration.between(run.getCreatedAt(), clock.instant()));
        } else if (gate == GateType.RELEASE_APPROVAL) {
            run.transitionTo(WorkflowStatus.COMPLETED, clock.instant());
            metrics.outcome("completed", java.time.Duration.between(run.getCreatedAt(), clock.instant()));
        } else {
            run.transitionTo(WorkflowStatus.RUNNING, clock.instant());
        }
        runs.save(run);
        audit.record(workflowId, revision, correlationId, "APPROVAL_DECISION", actor,
                gate + " " + decision + " artifactHash=" + reviewedHash + " reason=" + reason);
        if (decision == ApprovalDecision.APPROVED && gate == GateType.CHANGE_APPROVAL) {
            Runnable resume = () -> scenarios.resumeAfterChangeApproval(workflowId, revision, correlationId);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { resume.run(); }
                });
            } else resume.run();
        }
        return approval;
    }

    @Transactional
    public void invalidateForNewRevision(UUID workflowId, ActorIdentity actor, String correlationId) {
        List<ApprovalEntity> current = approvals.findByWorkflowIdAndValidTrue(workflowId);
        current.forEach(ApprovalEntity::invalidate);
        approvals.saveAll(current);
        if (!current.isEmpty()) audit.record(workflowId, null, correlationId, "APPROVAL_INVALIDATED", actor,
                "Invalidated " + current.size() + " approvals after workflow revision changed");
    }

    private String artifactSetHash(Map<String, String> artifactHashes) {
        if (artifactHashes == null || artifactHashes.isEmpty()
                || artifactHashes.entrySet().stream().anyMatch(entry -> entry.getKey().isBlank()
                || !entry.getValue().matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("At least one named SHA-256 artifact hash is required");
        String canonical = artifactHashes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).reduce("", (a, b) -> a + b + "\n");
        return hashes.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private void verifyCurrentArtifacts(UUID workflowId, int revision, Map<String, String> artifactHashes) {
        if (artifactHashes == null) throw new IllegalArgumentException("Artifact hashes are required");
        artifactHashes.forEach((key, suppliedHash) -> {
            ContextArtifactEntity artifact = artifacts
                    .findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                            workflowId, revision, key)
                    .orElseThrow(() -> new IllegalArgumentException("Reviewed artifact is absent from current revision: " + key));
            if (!artifact.getContentHash().equals(suppliedHash))
                throw new IllegalArgumentException("Reviewed artifact hash is stale or incorrect: " + key);
        });
    }

    private void requireGateEvidence(GateType gate, Map<String, String> artifactHashes) {
        String requiredArtifact = switch (gate) {
            case CHANGE_APPROVAL -> "engineering-plan";
            case RELEASE_APPROVAL -> "engineering-outcome";
            default -> throw new IllegalArgumentException("Unsupported approval gate");
        };
        if (artifactHashes == null || !artifactHashes.containsKey(requiredArtifact)) {
            throw new IllegalArgumentException(gate + " requires current-revision artifact: " + requiredArtifact);
        }
    }

    private void requireRole(GateType gate, String role) {
        String required = gate == GateType.RELEASE_APPROVAL ? "ROLE_RELEASE_APPROVER" : "ROLE_APPROVER";
        if (!required.equals(role)) throw new SecurityException("Gate requires " + required);
        if (gate != GateType.CHANGE_APPROVAL && gate != GateType.RELEASE_APPROVAL)
            throw new IllegalArgumentException("Unsupported approval gate");
    }
}
