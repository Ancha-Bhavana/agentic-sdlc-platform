package bhavana.agenticsdlc.platform.scenario;

import bhavana.agenticsdlc.platform.api.WorkflowApplicationService;
import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import bhavana.agenticsdlc.platform.governance.*;
import bhavana.agenticsdlc.platform.workflow.domain.*;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import bhavana.agenticsdlc.platform.workflow.coordination.PersistentWorkflowCoordinator;
import bhavana.agenticsdlc.platform.repository.ManifestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import bhavana.agenticsdlc.platform.validation.*;
import java.nio.file.Files;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scenarios;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate",
        "agentic-sdlc.model.provider=deterministic"})
class DeterministicScenarioIntegrationTest {
    private static final Path REPOSITORY = Path.of("").toAbsolutePath().normalize();
    @DynamicPropertySource static void repositoryRoot(DynamicPropertyRegistry registry) {
        registry.add("agentic-sdlc.repository.approved-root", () -> REPOSITORY.getParent().toString());
    }
    private static final ActorIdentity OPERATOR = new ActorIdentity("scenario-operator", "ROLE_OPERATOR");
    private static final ActorIdentity APPROVER = new ActorIdentity("change-reviewer", "ROLE_APPROVER");
    private static final ActorIdentity RELEASE = new ActorIdentity("release-reviewer", "ROLE_RELEASE_APPROVER");
    @Autowired WorkflowApplicationService workflows;
    @Autowired ApprovalService approvals;
    @Autowired WorkflowTaskRepository tasks;
    @Autowired ContextArtifactRepository artifacts;
    @Autowired WorkflowExecutionSpecRepository executionSpecs;
    @Autowired DeterministicScenarioExecutor executor;
    @Autowired PersistentWorkflowCoordinator coordinator;
    @MockitoBean BuildRunner builds;

    @BeforeEach void executableValidation() {
        when(builds.run(any(), any(), any(), any())).thenAnswer(call -> {
            Path workspace = call.getArgument(0);
            boolean broken;
            try (var files = Files.walk(workspace)) {
                broken = files.filter(path -> path.toString().endsWith(".java")).anyMatch(path -> {
                    try { return Files.readString(path).contains("toLowerCase(java.util.Locale.ROOT)\n"); }
                    catch (Exception ignored) { return false; }
                });
            }
            return new ValidationResult(BuildCapability.MAVEN_VERIFY, broken ? 1 : 0, false,
                    broken ? "" : "Tests run: generated workflow test passed",
                    broken ? "COMPILATION ERROR: ';' expected" : "", Duration.ofMillis(25),
                    broken ? ValidationResult.FailureType.COMPILATION : ValidationResult.FailureType.NONE);
        });
    }

    @Test void greenfieldRunsThroughTwoHumanApprovalGates() {
        var run = workflows.submit("Build a production URL shortener", REPOSITORY.toString(), ScenarioType.GREENFIELD,
                UUID.randomUUID().toString(), OPERATOR);
        awaitStatus(run.getId(), WorkflowStatus.AWAITING_APPROVAL);
        assertTask(run.getId(), 1, "design", TaskStatus.SUCCEEDED);

        approvals.decide(run.getId(), 1, GateType.CHANGE_APPROVAL, ApprovalDecision.APPROVED,
                hash(run.getId(), 1, "engineering-plan"), "Design reviewed", APPROVER, run.getCorrelationId());
        awaitStatus(run.getId(), WorkflowStatus.AWAITING_APPROVAL);
        assertTask(run.getId(), 1, "validate", TaskStatus.SUCCEEDED);
        String mutation = artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                run.getId(), 1, "generated-source-mutation").orElseThrow().getContentJson();
        assertThat(mutation).contains("GeneratedUrlPolicy", "src/main/java", "src/test/java", "+++ b/");

        approvals.decide(run.getId(), 1, GateType.RELEASE_APPROVAL, ApprovalDecision.APPROVED,
                hash(run.getId(), 1, "engineering-outcome"), "Evidence reviewed", RELEASE, run.getCorrelationId());
        assertThat(workflows.require(run.getId()).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test void brownfieldRepairScenarioRecordsFailureDrivenAdaptation() {
        var run = workflows.submit("Run the repair scenario while adding redirect analytics", REPOSITORY.toString(),
                ScenarioType.BROWNFIELD, UUID.randomUUID().toString(), OPERATOR);
        awaitStatus(run.getId(), WorkflowStatus.AWAITING_APPROVAL);
        approvals.decide(run.getId(), 1, GateType.CHANGE_APPROVAL, ApprovalDecision.APPROVED,
                hash(run.getId(), 1, "engineering-plan"), "Impact reviewed", APPROVER, run.getCorrelationId());
        awaitStatus(run.getId(), WorkflowStatus.AWAITING_APPROVAL);
        String evidence = artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                run.getId(), 1, "validation-summary").orElseThrow().getContentJson();
        assertThat(evidence).contains("\"attempts\":2", "\"repaired\":true");
        assertThat(artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                run.getId(), 1, "validation-attempt-1").orElseThrow().getContentJson())
                .contains("COMPILATION", "';' expected");
        assertThat(artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                run.getId(), 1, "validation-attempt-2").orElseThrow().getContentJson())
                .contains("\"exitCode\":0");
    }

    @Test void ambiguousScenarioPausesClarifiesAndSelectivelyReplans() {
        var run = workflows.submit("Make URL analytics better", REPOSITORY.toString(), ScenarioType.AMBIGUOUS,
                UUID.randomUUID().toString(), OPERATOR);
        awaitStatus(run.getId(), WorkflowStatus.AWAITING_CLARIFICATION);
        assertThat(artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                run.getId(), 1, "clarification-request")).isPresent();

        workflows.clarify(run.getId(), "Track total redirects and daily UTC counts", REPOSITORY.toString(),
                run.getCorrelationId(), OPERATOR);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var current = workflows.require(run.getId());
            assertThat(current.getCurrentRevision()).isEqualTo(2);
            assertThat(current.getStatus()).isEqualTo(WorkflowStatus.AWAITING_APPROVAL);
        });
        assertTask(run.getId(), 2, "understand", TaskStatus.REUSED);
        assertTask(run.getId(), 2, "design", TaskStatus.SUCCEEDED);

        approvals.decide(run.getId(), 2, GateType.CHANGE_APPROVAL, ApprovalDecision.APPROVED,
                hash(run.getId(), 2, "engineering-plan"), "Clarified design reviewed", APPROVER, run.getCorrelationId());
        awaitStatus(run.getId(), WorkflowStatus.AWAITING_APPROVAL);
        assertThat(artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                run.getId(), 2, "generated-source-mutation")).isPresent();
    }

    @Test void persistedRunningWorkflowIsAutomaticallyResumed() {
        UUID id = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        coordinator.submit(id, correlationId, "Recover this workflow", new ManifestService().capture(REPOSITORY));
        executionSpecs.save(new WorkflowExecutionSpecEntity(id, 1, ScenarioType.GREENFIELD,
                "Recover this workflow", REPOSITORY.toString(), correlationId, java.time.Instant.now()));

        executor.recoverInFlight();

        awaitStatus(id, WorkflowStatus.AWAITING_APPROVAL);
        assertTask(id, 1, "design", TaskStatus.SUCCEEDED);
    }

    private void awaitStatus(UUID id, WorkflowStatus status) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(workflows.require(id).getStatus()).isEqualTo(status));
    }
    private void assertTask(UUID id, int revision, String taskId, TaskStatus status) {
        var task = tasks.findById(new WorkflowTaskEntity.Key(id, revision, taskId)).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(status);
    }
    private Map<String, String> hash(UUID id, int revision, String key) {
        return Map.of(key, artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(
                id, revision, key).orElseThrow().getContentHash());
    }
}
