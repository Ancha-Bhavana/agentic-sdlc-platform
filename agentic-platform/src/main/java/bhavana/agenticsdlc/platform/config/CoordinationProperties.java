package bhavana.agenticsdlc.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("agentic-sdlc.coordination")
public record CoordinationProperties(String instanceId, Duration leaseDuration, Duration recoveryInterval,
                                     String runtimeRoot, String approvedRepositoryRoot) {
    public CoordinationProperties {
        instanceId = instanceId == null || instanceId.isBlank() ? "local-instance" : instanceId;
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(2) : leaseDuration;
        recoveryInterval = recoveryInterval == null ? Duration.ofSeconds(15) : recoveryInterval;
        runtimeRoot = runtimeRoot == null || runtimeRoot.isBlank()
                ? java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "agentic-sdlc-runtime").toString()
                : runtimeRoot;
        approvedRepositoryRoot = approvedRepositoryRoot == null || approvedRepositoryRoot.isBlank()
                ? "." : approvedRepositoryRoot;
        if (leaseDuration.isNegative() || leaseDuration.isZero())
            throw new IllegalArgumentException("Coordination lease duration must be positive");
    }
}
