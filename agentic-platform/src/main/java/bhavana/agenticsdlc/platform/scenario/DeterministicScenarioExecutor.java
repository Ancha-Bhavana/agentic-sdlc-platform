package bhavana.agenticsdlc.platform.scenario;

import bhavana.agenticsdlc.platform.audit.AuditService;
import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import bhavana.agenticsdlc.platform.repository.FileHashService;
import bhavana.agenticsdlc.platform.repository.RepositoryContextSelector;
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
import bhavana.agenticsdlc.platform.agent.*;
import bhavana.agenticsdlc.platform.validation.*;
import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DeterministicScenarioExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(DeterministicScenarioExecutor.class);
    private static final ScheduledExecutorService HEARTBEATS = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "workflow-lease-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
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
    private final AgentCatalog agents;
    private final BuildRunner builds;
    private final String instanceId;
    private final Duration leaseDuration;
    private final RepositoryContextSelector repositoryContext = new RepositoryContextSelector(30, 48_000);
    public DeterministicScenarioExecutor(PersistentWorkflowCoordinator coordinator,
            ContextArtifactRepository artifacts, AuditService audit, ObjectMapper mapper, Clock clock,
            WorkflowMetrics metrics, WorkflowExecutionSpecRepository executionSpecs,
            WorkflowRunRepository runs, WorkflowTaskRepository tasks,
            GeneratedScenarioMutationService mutations, ApprovalRepository approvals,
            AgentCatalog agents, BuildRunner builds,
            CoordinationProperties coordination) {
        this.coordinator = coordinator; this.artifacts = artifacts; this.audit = audit;
        this.mapper = mapper; this.clock = clock; this.metrics = metrics;
        this.executionSpecs = executionSpecs; this.runs = runs; this.tasks = tasks; this.mutations = mutations;
        this.approvals = approvals;
        this.agents = agents; this.builds = builds;
        this.instanceId = coordination.instanceId(); this.leaseDuration = coordination.leaseDuration();
    }

    @Async("scenarioTaskExecutor")
    public void start(UUID id, int revision, ScenarioType type, String requirement, String correlationId) {
        try {
            metrics.workflowSubmitted(type.name());
            evidenceOnce(id, revision, "scenario-profile", "understand", Map.of("type", type, "requirement", requirement,
                    "repairScenario", requirement.toLowerCase(Locale.ROOT).contains("repair scenario")));
            completeWithAgent(id, revision, "understand", AgentRole.REQUIREMENT_UNDERSTANDING, requirement);
            completeWithAgent(id, revision, "ambiguity", AgentRole.AMBIGUITY_CLARIFICATION, requirement);
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
        completeWithAgent(id, revision, "ambiguity", AgentRole.AMBIGUITY_CLARIFICATION, requirement);
        runDesign(id, revision, ScenarioType.AMBIGUOUS, correlationId);
    }

    @Async("scenarioTaskExecutor")
    public void resumeAfterChangeApproval(UUID id, int revision, String correlationId) {
        try { executeAfterChangeApproval(id, revision, correlationId); }
        catch (RuntimeException failure) {
            if (isClaimUnavailable(failure)) return;
            LOG.error("Workflow {} revision {} failed after change approval", id, revision, failure);
            audit.system(id, revision, correlationId, "SCENARIO_EXECUTION_FAILED", failure.getClass().getSimpleName());
            coordinator.fail(id); metrics.outcome("failed", Duration.ZERO);
        }
    }

    private void executeAfterChangeApproval(UUID id, int revision, String correlationId) {
        Instant started = clock.instant();
        CancellationToken cancellation = new CancellationToken();
        coordinator.registerActiveExecution(id, revision, cancellation, () -> mutations.rollback(id, revision));
        try {
        boolean repair = artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                id, revision, "scenario-profile").map(ContextArtifactEntity::getContentJson)
                .map(value -> value.contains("\"repairScenario\":true")).orElse(false);
        WorkflowExecutionSpecEntity spec = requireSpec(id, revision);
        final GeneratedScenarioMutationService.MutationEvidence[] applied = new GeneratedScenarioMutationService.MutationEvidence[1];
        CompletableFuture<Void> implementation = CompletableFuture.runAsync(() -> completeWithAction(id, revision, "implementation", () -> {
            invokeAgent(id, revision, "implementation", AgentRole.IMPLEMENTATION, spec.getRequirementText());
            var mutation = mutations.mutate(id, revision, spec.getScenarioType(), Path.of(spec.getRepositoryPath()),
                    spec.getRequirementText(), repair);
            applied[0] = mutation;
            evidence(id, revision, "generated-source-mutation", "implementation", Map.of(
                    "paths", mutation.paths(), "manifestHash", mutation.manifestHash(), "diff", mutation.diff(),
                    "deliberatelyBroken", mutation.deliberatelyBroken()));
        }));
        CompletableFuture<Void> testsBranch = CompletableFuture.runAsync(() ->
                completeWithAgent(id, revision, "tests", AgentRole.TEST_GENERATION, spec.getRequirementText()));
        CompletableFuture.allOf(implementation, testsBranch).join();
        for (String task : List.of("patch-policy", "apply")) complete(id, revision, task);
        ValidationResult validation = builds.run(applied[0].workspacePath(), BuildCapability.MAVEN_VERIFY,
                Duration.ofMinutes(3), cancellation);
        evidence(id, revision, "validation-attempt-1", "validate", validationEvidence(validation));
        if (!validation.successful()) {
            metrics.retry("validation");
            if (!repair) throw new IllegalStateException("Generated change failed validation");
            invokeAgent(id, revision, "repair", AgentRole.REPAIR,
                    bounded(validation.standardOutput() + "\n" + validation.standardError()));
            var repaired = mutations.repair(id, revision, spec.getScenarioType(), Path.of(spec.getRepositoryPath()),
                    spec.getRequirementText());
            evidence(id, revision, "repair-patch", "repair", Map.of("paths", repaired.paths(),
                    "manifestHash", repaired.manifestHash(), "diff", repaired.diff()));
            validation = builds.run(repaired.workspacePath(), BuildCapability.MAVEN_VERIFY,
                    Duration.ofMinutes(3), cancellation);
            evidence(id, revision, "validation-attempt-2", "validate", validationEvidence(validation));
            if (!validation.successful()) throw new IllegalStateException("Repair budget exhausted");
        }
        complete(id, revision, "validate");
        complete(id, revision, "repair");
        evidence(id, revision, "validation-summary", "repair", Map.of("successful", true,
                "attempts", repair ? 2 : 1, "repaired", repair, "command", "Maven Wrapper verify",
                "exitCode", validation.exitCode(), "durationMillis", validation.duration().toMillis()));
        if (repair) metrics.repair(Duration.between(started, clock.instant()), true);
        completeWithAgent(id, revision, "documentation", AgentRole.DOCUMENTATION, spec.getRequirementText());
        completeWithAgent(id, revision, "risk", AgentRole.SECURITY_RISK_REVIEW, spec.getRequirementText());
        completeWithAgent(id, revision, "release", AgentRole.RELEASE_READINESS, spec.getRequirementText());
        evidence(id, revision, "release-evidence", "release", Map.of("testsPassed", true,
                "rollbackAvailable", true, "humanApprovalRequired", true));
        evidence(id, revision, "engineering-outcome", "release", outcome(id, revision, spec, repair));
        coordinator.pauseForApproval(id);
        audit.system(id, revision, correlationId, "RELEASE_APPROVAL_REQUIRED", "Validated exact revision awaiting release approval");
        } finally {
            coordinator.completeActiveExecution(id, revision);
        }
    }

    private void runDesign(UUID id, int revision, ScenarioType type, String correlationId) {
        WorkflowExecutionSpecEntity spec = requireSpec(id, revision);
        completeWithAgent(id, revision, "repository", AgentRole.REPOSITORY_ANALYSIS, spec.getRequirementText());
        completeWithAgent(id, revision, "decompose", AgentRole.TASK_DECOMPOSITION, spec.getRequirementText());
        completeWithAgent(id, revision, "design", AgentRole.ARCHITECTURE_DESIGN, spec.getRequirementText());
        evidence(id, revision, "engineering-plan", "design", Map.of("scenario", type,
                "parallelTasks", List.of("implementation", "tests"),
                "synchronization", "patch-policy", "changeApprovalRequired", true));
        coordinator.pauseForApproval(id);
        audit.system(id, revision, correlationId, "CHANGE_APPROVAL_REQUIRED", "Design complete; exact revision awaits approval");
    }

    private void complete(UUID id, int revision, String task) {
        completeWithAction(id, revision, task, () -> {});
    }

    private void completeWithAgent(UUID id, int revision, String task, AgentRole role, String requirement) {
        completeWithAction(id, revision, task, () -> invokeAgent(id, revision, task, role, requirement));
    }

    private void invokeAgent(UUID id, int revision, String task, AgentRole role, String requirement) {
        Map<String, String> upstream = upstreamContext(id, revision);
        Path repository = Path.of(requireSpec(id, revision).getRepositoryPath());
        Map<String, String> selectedRepository = Set.of(AgentRole.REQUIREMENT_UNDERSTANDING,
                AgentRole.AMBIGUITY_CLARIFICATION).contains(role)
                ? Map.of() : repositoryContext.select(repository, Set.of());
        AgentResult result = agents.require(role).execute(new AgentRequest(id.toString(), revision, requirement,
                upstream, selectedRepository));
        evidence(id, revision, "agent-" + task, task, result);
        audit.system(id, revision, requireSpec(id, revision).getCorrelationId(), "AGENT_COMPLETED",
                role.name() + " produced agent-" + task);
    }

    private Map<String, String> upstreamContext(UUID id, int revision) {
        List<ContextArtifactEntity> values = new ArrayList<>(
                artifacts.findByWorkflowIdAndWorkflowRevisionOrderByCreatedAtAsc(id, revision));
        Collections.reverse(values);
        Map<String, String> selected = new TreeMap<>(); int remaining = 48_000;
        for (ContextArtifactEntity value : values) {
            if (selected.containsKey(value.getArtifactKey()) || remaining == 0) continue;
            String content = value.getContentJson();
            int size = Math.min(content.length(), remaining);
            selected.put(value.getArtifactKey(), content.substring(0, size)); remaining -= size;
        }
        return Map.copyOf(selected);
    }

    private Map<String, Object> validationEvidence(ValidationResult result) {
        return Map.of("exitCode", result.exitCode(), "timedOut", result.timedOut(),
                "failureType", result.failureType(), "durationMillis", result.duration().toMillis(),
                "standardOutput", bounded(result.standardOutput()), "standardError", bounded(result.standardError()));
    }

    private String bounded(String value) {
        if (value == null) return "";
        return value.length() <= 16_000 ? value : value.substring(0, 16_000) + "\n[artifact truncated]";
    }

    private Map<String, Object> outcome(UUID id, int revision, WorkflowExecutionSpecEntity spec, boolean repaired) {
        Map<String, String> selected = new LinkedHashMap<>();
        for (String key : List.of("engineering-plan", "generated-source-mutation", "validation-summary",
                "repair-patch", "agent-documentation", "agent-risk", "agent-release"))
            artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(id, revision, key)
                    .ifPresent(value -> selected.put(key, value.getContentHash()));
        return Map.of("workflowId", id, "revision", revision, "scenario", spec.getScenarioType(),
                "requirement", spec.getRequirementText(), "artifactHashes", selected,
                "validation", "Maven Wrapper verify passed", "repaired", repaired,
                "decision", "Awaiting authenticated release approval",
                "assumptions", List.of("Repository is Maven Wrapper enabled"),
                "limitations", List.of("Generated changes remain isolated until a human promotes them"));
    }

    private void completeWithAction(UUID id, int revision, String task, Runnable action) {
        TaskStatus current = tasks.findById(new WorkflowTaskEntity.Key(id, revision, task)).orElseThrow().getStatus();
        if (current.satisfiesDependency()) return;
        OptionalLong claim = coordinator.claimTask(id, revision, task, instanceId, leaseDuration);
        if (claim.isEmpty()) throw new ClaimUnavailableException();
        long heartbeatMillis = Math.max(1_000L, leaseDuration.toMillis() / 3L);
        ScheduledFuture<?> heartbeat = HEARTBEATS.scheduleAtFixedRate(() -> {
            try {
                coordinator.heartbeatTask(id, revision, task, instanceId, claim.getAsLong(), leaseDuration);
            } catch (RuntimeException failure) {
                LOG.warn("Could not renew lease for workflow {} revision {} task {}", id, revision, task, failure);
            }
        }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            action.run();
            coordinator.finishClaimedTask(id, revision, task, instanceId, claim.getAsLong(), true);
        } catch (RuntimeException failure) {
            coordinator.finishClaimedTask(id, revision, task, instanceId, claim.getAsLong(), false);
            throw failure;
        } finally {
            heartbeat.cancel(true);
        }
        metrics.task(task, "success");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAtStartup() { recoverInFlight(); }

    @Scheduled(fixedDelayString = "${agentic-sdlc.coordination.recovery-interval:15s}")
    public void recoverInFlight() {
        for (WorkflowRunEntity run : runs.findByStatusIn(List.of(WorkflowStatus.RUNNING))) {
            try {
                if (coordinator.isActiveExecution(run.getId())) continue;
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

    private boolean isClaimUnavailable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause())
            if (current instanceof ClaimUnavailableException) return true;
        return false;
    }

    private void evidence(UUID id, int revision, String key, String producer, Object value) {
        try {
            String json = mapper.writeValueAsString(value);
            long version = artifacts.countByWorkflowIdAndArtifactKey(id, key) + 1;
            Map<String, String> lineage = new LinkedHashMap<>();
            artifacts.findByWorkflowIdAndWorkflowRevisionOrderByCreatedAtAsc(id, revision)
                    .forEach(existing -> lineage.put(existing.getArtifactKey(), existing.getContentHash()));
            artifacts.save(new ContextArtifactEntity(id, revision, key, version, producer,
                    hashes.sha256(json.getBytes(StandardCharsets.UTF_8)), mapper.writeValueAsString(lineage),
                    json, clock.instant()));
        } catch (Exception failure) { throw new IllegalStateException("Cannot persist scenario evidence", failure); }
    }
}
