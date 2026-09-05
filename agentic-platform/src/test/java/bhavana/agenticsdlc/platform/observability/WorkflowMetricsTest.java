package bhavana.agenticsdlc.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowMetricsTest {
    @Test void recordsRequiredReliabilitySignals() {
        var registry = new SimpleMeterRegistry();
        var metrics = new WorkflowMetrics(registry);
        metrics.workflowSubmitted("BROWNFIELD"); metrics.task("validate", "success");
        metrics.retry("validation"); metrics.repair(Duration.ofSeconds(2), true);
        metrics.rollback("safe-stop"); metrics.outcome("completed", Duration.ofSeconds(8));
        assertThat(registry.get("agentic.workflow.submitted").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentic.workflow.retries").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentic.workflow.repairs").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentic.workflow.rollbacks").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentic.workflow.duration").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(8.0);
    }
}
