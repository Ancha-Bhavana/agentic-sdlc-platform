package bhavana.agenticsdlc.platform.workflow;

import bhavana.agenticsdlc.platform.agent.*;
import bhavana.agenticsdlc.platform.model.*;
import bhavana.agenticsdlc.platform.repository.*;
import bhavana.agenticsdlc.platform.validation.*;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;
import bhavana.agenticsdlc.platform.workflow.execution.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class LifecycleExecutionTest {
    @TempDir Path root;

    @Test void usesActualValidationFailureToDriveBoundedRepairAndRecordsEvidence() throws Exception {
        Path source = root.resolve("approved/repository");
        Files.createDirectories(source.resolve("src/main/java"));
        Files.writeString(source.resolve("pom.xml"), "<project/>");
        var workspaces = new WorkspaceService(root.resolve("runtime"), root.resolve("approved"));
        var handle = workspaces.create(UUID.randomUUID(), 1, source);
        ObjectMapper mapper = new ObjectMapper();
        String broken = "class Generated { BROKEN }";
        String brokenHash = new FileHashService().sha256(broken.getBytes(StandardCharsets.UTF_8));
        ModelProvider provider = (request, timeout) -> new ModelResponse("fixture", "1", response(mapper, request.operation(), broken, brokenHash));
        var agents = new AgentCatalog(new ValidatedModelGateway(provider, mapper), mapper);
        AtomicInteger builds = new AtomicInteger();
        BuildRunner runner = (workspace, capability, timeout, token) -> {
            int attempt = builds.incrementAndGet();
            return new ValidationResult(capability, attempt == 1 ? 1 : 0, false, "",
                    attempt == 1 ? "COMPILATION FAILURE: cannot find symbol BROKEN" : "",
                    Duration.ofMillis(1), attempt == 1 ? ValidationResult.FailureType.COMPILATION : ValidationResult.FailureType.NONE);
        };
        var lifecycle = new LifecycleExecution(handle, workspaces, "Create generated Java source",
                agents, runner, mapper, 2, Duration.ofSeconds(1));

        var report = lifecycle.run((task, gate, phase) -> GateEvaluator.GateDecision.PASS,
                Duration.ofSeconds(10), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(builds).hasValue(2);
        assertThat(Files.readString(handle.path().resolve("src/main/java/Generated.java")))
                .isEqualTo("class Generated {}");
        assertThat(lifecycle.artifacts().history("validation-evidence")).hasSize(2);
        assertThat(lifecycle.artifacts().latest("diff")).isPresent();
        assertThat(lifecycle.artifacts().latest("diff").orElseThrow().content()).contains("Generated.java");
    }

    private static String response(ObjectMapper mapper, AgentRole role, String broken, String brokenHash) {
        try {
            Map<String, Object> content = switch (role) {
                case IMPLEMENTATION -> Map.of("operations", List.of(Map.of("type", "CREATE",
                        "path", "src/main/java/Generated.java", "content", broken)));
                case TEST_GENERATION -> Map.of("operations", List.of(Map.of("type", "CREATE",
                        "path", "src/test/java/GeneratedTest.java", "content", "class GeneratedTest {}")));
                case REPAIR -> Map.of("operations", List.of(Map.of("type", "UPDATE",
                        "path", "src/main/java/Generated.java", "expectedHash", brokenHash,
                        "content", "class Generated {}")));
                default -> Map.of("role", role.name());
            };
            return mapper.writeValueAsString(Map.of(
                    "reasoningSummary", "Used supplied evidence for " + role,
                    "assumptions", List.of(), "decisions", List.of(), "risks", List.of(),
                    "evidence", List.of("structured fixture"),
                    "artifact", Map.of("kind", role.name().toLowerCase(), "schemaVersion", "1.0", "content", content)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
