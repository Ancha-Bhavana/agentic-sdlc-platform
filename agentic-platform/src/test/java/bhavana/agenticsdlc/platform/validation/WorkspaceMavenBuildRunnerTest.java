package bhavana.agenticsdlc.platform.validation;

import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class WorkspaceMavenBuildRunnerTest {
    @TempDir Path workspace;

    @Test void executesOnlyFixedWrapperArgumentsAndCapturesFailure() throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path wrapper = workspace.resolve(windows ? "mvnw.cmd" : "mvnw");
        Files.writeString(wrapper, windows
                ? "@echo off\r\necho compiler failure 1>&2\r\nexit /b 7\r\n"
                : "#!/bin/sh\necho compiler failure >&2\nexit 7\n");
        var result = new WorkspaceMavenBuildRunner(1024).run(workspace, BuildCapability.MAVEN_VERIFY,
                Duration.ofSeconds(5), new CancellationToken());
        assertThat(result.exitCode()).isEqualTo(7);
        assertThat(result.standardError()).contains("compiler failure");
        assertThat(result.successful()).isFalse();
    }

    @Test void rejectsRepositoryControlledMavenConfiguration() throws Exception {
        Files.writeString(workspace.resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "mvnw.cmd" : "mvnw"), "ignored");
        Files.createDirectories(workspace.resolve(".mvn"));
        Files.writeString(workspace.resolve(".mvn/maven.config"), "-Dunsafe=true");
        var result = new WorkspaceMavenBuildRunner(1024).run(workspace, BuildCapability.MAVEN_VERIFY,
                Duration.ofSeconds(5), new CancellationToken());
        assertThat(result.failureType()).isEqualTo(ValidationResult.FailureType.TOOL);
        assertThat(result.standardError()).contains("Untrusted Maven configuration");
    }
}
