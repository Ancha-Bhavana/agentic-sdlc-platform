package bhavana.agenticsdlc.platform.validation;

import java.time.Duration;

public record ValidationResult(BuildCapability capability, int exitCode, boolean timedOut,
                               String standardOutput, String standardError, Duration duration,
                               FailureType failureType) {
    public boolean successful() { return !timedOut && exitCode == 0; }
    public enum FailureType { NONE, COMPILATION, TEST, TIMEOUT, TOOL, UNKNOWN }
}
