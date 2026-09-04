package bhavana.agenticsdlc.platform.validation;

import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import java.nio.file.Path;
import java.time.Duration;

@FunctionalInterface
public interface BuildRunner {
    ValidationResult run(Path workspace, BuildCapability capability, Duration timeout, CancellationToken token);
}
