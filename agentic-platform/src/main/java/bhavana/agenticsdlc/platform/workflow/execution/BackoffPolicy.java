package bhavana.agenticsdlc.platform.workflow.execution;

import java.time.Duration;

@FunctionalInterface
public interface BackoffPolicy {
    Duration delayAfterFailure(int failedAttempt);

    static BackoffPolicy exponential(Duration initial, Duration maximum) {
        if (initial.isNegative() || maximum.isNegative() || initial.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Backoff bounds are invalid");
        }
        return failedAttempt -> {
            long multiplier = 1L << Math.min(Math.max(failedAttempt - 1, 0), 30);
            Duration candidate;
            try {
                candidate = initial.multipliedBy(multiplier);
            } catch (ArithmeticException overflow) {
                candidate = maximum;
            }
            return candidate.compareTo(maximum) > 0 ? maximum : candidate;
        };
    }
}

