package bhavana.agenticsdlc.platform.agent;
import java.time.Duration;
public record AgentLimits(int maximumContextBytes, int maximumOutputBytes, int maximumAttempts, Duration timeout) {
    public AgentLimits { if (maximumContextBytes < 1 || maximumOutputBytes < 1 || maximumAttempts < 1 || timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Agent limits must be positive"); }
}
