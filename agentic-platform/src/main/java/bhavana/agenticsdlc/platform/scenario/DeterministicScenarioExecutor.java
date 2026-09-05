package bhavana.agenticsdlc.platform.scenario;

import bhavana.agenticsdlc.platform.audit.AuditService;
import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import bhavana.agenticsdlc.platform.repository.FileHashService;
import bhavana.agenticsdlc.platform.workflow.coordination.PersistentWorkflowCoordinator;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class DeterministicScenarioExecutor {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final PersistentWorkflowCoordinator coordinator;
    private final ContextArtifactRepository artifacts;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final FileHashService hashes = new FileHashService();
    public DeterministicScenarioExecutor(PersistentWorkflowCoordinator coordinator,
            ContextArtifactRepository artifacts, AuditService audit, ObjectMapper mapper, Clock clock) {
        this.coordinator = coordinator; this.artifacts = artifacts; this.audit = audit;
        this.mapper = mapper; this.clock = clock;
    }

    @Async("scenarioTaskExecutor")
    public void start(UUID id, int revision, ScenarioType type, String requirement, String correlationId) {
        try {
            evidence(id, revision, "scenario-profile", "understand", Map.of("type", type, "requirement", requirement,
                    "repairScenario", requirement.toLowerCase(Locale.ROOT).contains("repair scenario")));
            complete(id, revision, "understand");
            complete(id, revision, "ambiguity");
            if (type == ScenarioType.AMBIGUOUS) {
                evidence(id, revision, "clarification-request", "ambiguity", Map.of(
                        "questions", List.of("Which analytics measures are required?", "Which timezone defines a day?"),
                        "reason", "Analytics scope and time boundary affect schema and API design"));
                coordinator.pauseForClarification(id);
                audit.system(id, revision, correlationId, "CLARIFICATION_REQUIRED", "Workflow paused before repository changes");
                return;
            }
            runDesign(id, revision, type, correlationId);
        } catch (RuntimeException failure) {
            audit.system(id, revision, correlationId, "SCENARIO_EXECUTION_FAILED", failure.getClass().getSimpleName());
            coordinator.fail(id);
        }
    }

    @Async("scenarioTaskExecutor")
    public void resumeAfterClarification(UUID id, int revision, String requirement, String correlationId) {
        evidence(id, revision, "clarification-resolution", "ambiguity", Map.of("normalizedRequirement", requirement,
                "decisions", List.of("Count total redirects", "Aggregate daily counts in UTC")));
        runDesign(id, revision, ScenarioType.AMBIGUOUS, correlationId);
    }

    @Async("scenarioTaskExecutor")
    public void resumeAfterChangeApproval(UUID id, int revision, String correlationId) {
        boolean repair = artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                id, revision, "scenario-profile").map(ContextArtifactEntity::getContentJson)
                .map(value -> value.contains("\"repairScenario\":true")).orElse(false);
        for (String task : List.of("implementation", "tests", "patch-policy", "apply")) complete(id, revision, task);
        if (repair) {
            evidence(id, revision, "validation-attempt-1", "validate", Map.of("exitCode", 1,
                    "classification", "TEST_FAILURE", "action", "invoke repair agent"));
        }
        complete(id, revision, "validate");
        complete(id, revision, "repair");
        evidence(id, revision, "validation-summary", "repair", Map.of("successful", true,
                "attempts", repair ? 2 : 1, "repaired", repair, "command", "mvnw clean verify"));
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
        coordinator.taskStarted(id, revision, task, LEASE);
        coordinator.taskFinished(id, revision, task, true);
    }

    private void evidence(UUID id, int revision, String key, String producer, Object value) {
        try {
            String json = mapper.writeValueAsString(value);
            long version = artifacts.countByWorkflowIdAndArtifactKey(id, key) + 1;
            artifacts.save(new ContextArtifactEntity(id, revision, key, version, producer,
                    hashes.sha256(json.getBytes(StandardCharsets.UTF_8)), "{}", json, clock.instant()));
        } catch (Exception failure) { throw new IllegalStateException("Cannot persist scenario evidence", failure); }
    }
}
