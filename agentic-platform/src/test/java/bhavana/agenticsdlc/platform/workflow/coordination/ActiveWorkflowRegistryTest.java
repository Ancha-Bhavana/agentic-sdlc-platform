package bhavana.agenticsdlc.platform.workflow.coordination;

import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ActiveWorkflowRegistryTest {
    @Test void safeStopCancelsExecutionAndRunsRollbackExactlyOnce() {
        UUID workflowId = UUID.randomUUID();
        CancellationToken token = new CancellationToken();
        AtomicInteger rollbacks = new AtomicInteger();
        ActiveWorkflowRegistry registry = new ActiveWorkflowRegistry();
        registry.register(workflowId, 2, token, rollbacks::incrementAndGet);

        registry.safeStop(workflowId, 2);

        assertThat(token.isCancelled()).isTrue();
        assertThat(rollbacks).hasValue(1);
        assertThat(registry.isActive(workflowId)).isFalse();
        assertThatThrownBy(() -> registry.safeStop(workflowId, 2))
                .isInstanceOf(IllegalStateException.class);
    }
}
