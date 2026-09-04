package bhavana.agenticsdlc.platform.workflow.persistence;

import bhavana.agenticsdlc.platform.workflow.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class WorkflowTaskEntityTest {
    private static TaskDefinition definition() {
        return new TaskDefinition("analysis", TaskType.REPOSITORY_ANALYSIS, Set.of(),
                GateType.NONE, GateType.NONE, 2, Duration.ofSeconds(10));
    }

    @Test void expiredLeaseBecomesReadyAfterRestartAndCanBeClaimedAgain() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        WorkflowTaskEntity task = new WorkflowTaskEntity(UUID.randomUUID(), 1, definition(), TaskStatus.PENDING);
        task.start(start, Duration.ofSeconds(5));

        task.recoverIfLeaseExpired(start.plusSeconds(6));
        task.start(start.plusSeconds(7), Duration.ofSeconds(5));
        task.succeed(start.plusSeconds(8));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(task.getAttempt()).isEqualTo(2);
        assertThat(task.getLeaseExpiresAt()).isNull();
    }

    @Test void activeLeaseIsNotStolenDuringRecovery() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        WorkflowTaskEntity task = new WorkflowTaskEntity(UUID.randomUUID(), 1, definition(), TaskStatus.PENDING);
        task.start(start, Duration.ofSeconds(30));

        task.recoverIfLeaseExpired(start.plusSeconds(5));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }
}
