package bhavana.agenticsdlc.platform.observability;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public final class WorkflowMetrics {
    private final MeterRegistry registry;
    public WorkflowMetrics(MeterRegistry registry) { this.registry = registry; }
    public void workflowSubmitted(String scenario) {
        Counter.builder("agentic.workflow.submitted").tag("scenario", scenario).register(registry).increment();
    }
    public void outcome(String outcome, Duration duration) {
        Counter.builder("agentic.workflow.outcomes").tag("outcome", outcome).register(registry).increment();
        Timer.builder("agentic.workflow.duration").tag("outcome", outcome).publishPercentileHistogram()
                .register(registry).record(duration);
    }
    public void task(String task, String outcome) {
        Counter.builder("agentic.task.outcomes").tag("task", task).tag("outcome", outcome)
                .register(registry).increment();
    }
    public void retry(String reason) {
        Counter.builder("agentic.workflow.retries").tag("reason", reason).register(registry).increment();
    }
    public void repair(Duration duration, boolean successful) {
        Counter.builder("agentic.workflow.repairs").tag("outcome", successful ? "success" : "failure")
                .register(registry).increment();
        Timer.builder("agentic.workflow.repair.duration").tag("outcome", successful ? "success" : "failure")
                .register(registry).record(duration);
    }
    public void rollback(String reason) {
        Counter.builder("agentic.workflow.rollbacks").tag("reason", reason).register(registry).increment();
    }
}
