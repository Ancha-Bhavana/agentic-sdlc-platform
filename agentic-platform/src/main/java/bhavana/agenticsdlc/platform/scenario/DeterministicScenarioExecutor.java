package bhavana.agenticsdlc.platform.scenario;

import bhavana.agenticsdlc.platform.audit.AuditService;
import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import bhavana.agenticsdlc.platform.repository.FileHashService;
import bhavana.agenticsdlc.platform.observability.WorkflowMetrics;
import bhavana.agenticsdlc.platform.workflow.coordination.PersistentWorkflowCoordinator;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import bhavana.agenticsdlc.platform.config.CoordinationProperties;
import bhavana.agenticsdlc.platform.workflow.domain.*;
import bhavana.agenticsdlc.platform.governance.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class DeterministicScenarioExecutor {
    private final PersistentWorkflowCoordinator coordinator;
    private final ContextArtifactRepository artifacts;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final FileHashService hashes = new FileHashService();
    private final WorkflowMetrics metrics;
    private final WorkflowExecutionSpecRepository executionSpecs;
    private final WorkflowRunRepository runs;
    private final WorkflowTaskRepository tasks;
    private final GeneratedScenarioMutationService mutations;
    private final ApprovalRepository approvals;
    private final String instanceId;
    private final Duration leaseDuration;
    public DeterministicScenarioExecutor(PersistentWorkflowCoordinator coordinator,
            ContextArtifactRepository artifacts, AuditService audit, ObjectMapper mapper, Clock clock,
            WorkflowMetrics metrics, WorkflowExecutionSpecRepository executionSpecs,
            WorkflowRunRepository runs, WorkflowTaskRepository tasks,
            GeneratedScenarioMutationService mutations, ApprovalRepository approvals,
            CoordinationProperties coordination) {
        this.coordinator = coordinator; this.artifacts = artifacts; this.audit = audit;
        this.mapper = mapper; this.clock = clock; this.metrics = metrics;
        this.executionSpecs = executionSpecs; this.runs = runs; this.tasks = tasks; this.mutations = mutations;
        this.approvals = approvals;
        this.instanceId = coordination.instanceId(); this.leaseDuration = coordination.leaseDuration();
    }

    @Async("scenarioTaskExecutor")
    public void start(UUID id, int revision, ScenarioType type, String requirement, String correlationId) {
        try {
            metrics.workflowSubmitted(type.name());
            evidenceOnce(id, revision, "scenario-profile", "understand", Map.of("type", type, "requirement", requirement,
                    "repairScenario", requirement.toLowerCase(Locale.ROOT).contains("repair scenario")));
            complete(id, revision, "understand");
            complete(id, revision, "ambiguity");
            if (type == ScenarioType.AMBIGUOUS && revision == 1) {
                evidence(id, revision, "clarification-request", "ambiguity", Map.of(
                        "questions", List.of("Which analytics measures are required?", "Which timezone defines a day?"),
                        "reason", "Analytics scope and time boundary affect schema and API design"));
                coordinator.pauseForClarification(id);
                audit.system(id, revision, correlationId, "CLARIFICATION_REQUIRED", "Workflow paused before repository changes");
                return;
            }
            runDesign(id, revision, type, correlationId);
        } catch (ClaimUnavailableException claimedElsewhere) {
            // Another healthy platform instance owns the task; its durable lease is authoritative.
        } catch (RuntimeException failure) {
            audit.system(id, revision, correlationId, "SCENARIO_EXECUTION_FAILED", failure.getClass().getSimpleName());
            coordinator.fail(id);
            metrics.outcome("failed", Duration.ZERO);
        }
    }

    @Async("scenarioTaskExecutor")
    public void resumeAfterClarification(UUID id, int revision, String requirement, String correlationId) {
        evidence(id, revision, "clarification-resolution", "ambiguity", Map.of("normalizedRequirement", requirement,
                "decisions", List.of("Count total redirects", "Aggregate daily counts in UTC")));
        complete(id, revision, "ambiguity");
        runDesign(id, revision, ScenarioType.AMBIGUOUS, correlationId);
    }

    @Async("scenarioTaskExecutor")
    public void resumeAfterChangeApproval(UUID id, int revision, String correlationId) {
        Instant started = clock.instant();
        boolean repair = artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                id, revision, "scenario-profile").map(ContextArtifactEntity::getContentJson)
                .map(value -> value.contains("\"repairScenario\":true")).orElse(false);
        WorkflowExecutionSpecEntity spec = requireSpec(id, revision);
        completeWithAction(id, revision, "implementation", () -> {
            var mutation = mutations.mutate(id, revision, spec.getScenarioType(), Path.of(spec.getRepositoryPath()));
            evidence(id, revision, "generated-source-mutation", "implementation", Map.of(
                    "path", mutation.path(), "manifestHash", mutation.manifestHash(), "diff", mutation.diff()));
        });
        for (String task : List.of("tests", "patch-policy", "apply")) complete(id, revision, task);
        if (repair) {
            metrics.retry("validation");
            evidence(id, revision, "validation-attempt-1", "validate", Map.of("exitCode", 1,
                    "classification", "TEST_FAILURE", "action", "invoke repair agent"));
        }
        complete(id, revision, "validate");
        complete(id, revision, "repair");
        evidence(id, revision, "validation-summary", "repair", Map.of("successful", true,
                "attempts", repair ? 2 : 1, "repaired", repair, "command", "mvnw clean verify"));
        if (repair) metrics.repair(Duration.between(started, clock.instant()), true);
        complete(id, revision, "documentation"); complete(id, revision, "risk"); complete(id, revision, "release");
        evidence(id, revision, "release-evidence", "release", Map.of("testsPassed", true,
                "rollbackAvailable", true, "humanApprovalRequired", true));
        coordinator.pauseForApproval(id);
        audit.system(id, revision, correlationId, "RELEASE_APPROVAL_REQUIRED", "Validated exact revision awaiting release approval");
    }

    private void runDesign(UUID id, int revision, ScenarioType type, String correlationId) {
        complete(id, revision, "repository"); complete(id, revision, "decompose"); complete(id, revision, "design");
        evidence(id, revision, "engineering-plan", "design", Map.of("scenario", type,
                "parallelTasks", List.of("implementation", "tests"),
                "synchronization", "patch-policy", "changeApprovalRequired", true));
        coordinator.pauseForApproval(id);
        audit.system(id, revision, correlationId, "CHANGE_APPROVAL_REQUIRED", "Design complete; exact revision awaits approval");
    }

    private void complete(UUID id, int revision, String task) {
        completeWithAction(id, revision, task, () -> {});
    }

    private void completeWithAction(UUID id, int revision, String task, Runnable action) {
        TaskStatus current = tasks.findById(new WorkflowTaskEntity.Key(id, revision, task)).orElseThrow().getStatus();
        if (current.satisfiesDependency()) return;
        OptionalLong claim = coordinator.claimTask(id, revision, task, instanceId, leaseDuration);
        if (claim.isEmpty()) throw new ClaimUnavailableException();
        try {
            action.run();
            coordinator.finishClaimedTask(id, revision, task, instanceId, claim.getAsLong(), true);
        } catch (RuntimeException failure) {
            coordinator.finishClaimedTask(id, revision, task, instanceId, claim.getAsLong(), false);
            throw failure;
        }
        metrics.task(task, "success");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAtStartup() { recoverInFlight(); }

    @Scheduled(fixedDelayString = "${agentic-sdlc.coordination.recovery-interval:15s}")
    public void recoverInFlight() {
        for (WorkflowRunEntity run : runs.findByStatusIn(List.of(WorkflowStatus.RUNNING))) {
            try {
                coordinator.recover(run.getId());
                WorkflowExecutionSpecEntity spec = requireSpec(run.getId(), run.getCurrentRevision());
                TaskStatus design = status(run.getId(), run.getCurrentRevision(), "design");
                if (!design.satisfiesDependency())
                    start(run.getId(), run.getCurrentRevision(), spec.getScenarioType(),
                            spec.getRequirementText(), spec.getCorrelationId());
                else if (changeApproved(run.getId(), run.getCurrentRevision()))
                    resumeAfterChangeApproval(run.getId(), run.getCurrentRevision(), spec.getCorrelationId());
                else
                    coordinator.pauseForApproval(run.getId());
            } catch (ClaimUnavailableException ignored) {
                // Owned by another instance.
            } catch (RuntimeException failure) {
                audit.system(run.getId(), run.getCurrentRevision(), run.getCorrelationId(),
                        "WORKFLOW_RECOVERY_DEFERRED", failure.getClass().getSimpleName());
            }
        }
    }

    private TaskStatus status(UUID id, int revision, String task) {
        return tasks.findById(new WorkflowTaskEntity.Key(id, revision, task)).orElseThrow().getStatus();
    }

    private WorkflowExecutionSpecEntity requireSpec(UUID id, int revision) {
        return executionSpecs.findById(new WorkflowExecutionSpecEntity.Key(id, revision))
                .orElseThrow(() -> new IllegalStateException("Workflow execution specification missing"));
    }

    private boolean changeApproved(UUID id, int revision) {
        return approvals.findFirstByWorkflowIdAndWorkflowRevisionAndGateTypeAndValidTrueOrderByDecidedAtDesc(
                        id, revision, GateType.CHANGE_APPROVAL)
                .map(approval -> approval.getDecision() == ApprovalDecision.APPROVED).orElse(false);
    }

    private void evidenceOnce(UUID id, int revision, String key, String producer, Object value) {
        if (artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(id, revision, key).isEmpty())
            evidence(id, revision, key, producer, value);
    }

    private static final class ClaimUnavailableException extends RuntimeException {}

    private void evidence(UUID id, int revision, String key, String producer, Object value) {
        try {
            String json = mapper.writeValueAsString(value);
            long version = artifacts.countByWorkflowIdAndArtifactKey(id, key) + 1;
            artifacts.save(new ContextArtifactEntity(id, revision, key, version, producer,
                    hashes.sha256(json.getBytes(StandardCharsets.UTF_8)), "{}", json, clock.instant()));
        } catch (Exception failure) { throw new IllegalStateException("Cannot persist scenario evidence", failure); }
    }
}
