package bhavana.agenticsdlc.platform.scenario;

import bhavana.agenticsdlc.platform.config.CoordinationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class GeneratedScenarioMutationServiceTest {
    @TempDir Path root;

    @Test void everyDeterministicScenarioProducesIdempotentCompilableSource() throws Exception {
        Path repository = Files.createDirectories(root.resolve("repository"));
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Path runtime = root.resolve("runtime");
        var properties = new CoordinationProperties("instance", Duration.ofSeconds(30),
                Duration.ofSeconds(5), runtime.toString(), root.toString());
        var service = new GeneratedScenarioMutationService(properties, root);

        for (ScenarioType type : ScenarioType.values()) {
            UUID workflow = UUID.randomUUID();
            var first = service.mutate(workflow, 1, type, repository, "Add safe custom aliases", false);
            var second = service.mutate(workflow, 1, type, repository, "Add safe custom aliases", false);
            Path source = runtime.resolve(workflow.toString()).resolve("revision-1/repository").resolve(first.paths().getFirst());

            assertThat(source).isRegularFile();
            assertThat(second.manifestHash()).isEqualTo(first.manifestHash());
            assertThat(second.diff()).isEqualTo(first.diff());
            assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, source.toString())).isZero();
        }
    }

    @Test void repairReplacesAReproducibleCompilerFailure() throws Exception {
        Path repository = Files.createDirectories(root.resolve("repair-repository"));
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Path runtime = root.resolve("repair-runtime");
        var service = new GeneratedScenarioMutationService(new CoordinationProperties("instance",
                Duration.ofSeconds(30), Duration.ofSeconds(5), runtime.toString(), root.toString()), root);
        UUID workflow = UUID.randomUUID();

        var broken = service.mutate(workflow, 1, ScenarioType.BROWNFIELD, repository,
                "Repair alias validation", true);
        Path source = broken.workspacePath().resolve(broken.paths().getFirst());
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, source.toString())).isNotZero();

        var repaired = service.repair(workflow, 1, ScenarioType.BROWNFIELD, repository,
                "Repair alias validation");
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, source.toString())).isZero();
        assertThat(repaired.diff()).contains("Unsafe alias");
    }
}
