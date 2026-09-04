package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRecoveryPlannerTest {
    @Test
    void makesOnlyExpiredRunningLeasesReadyForRecovery() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        List<TaskCheckpoint> checkpoints = List.of(
                new TaskCheckpoint("expired", TaskStatus.RUNNING, now.minusSeconds(1)),
                new TaskCheckpoint("owned", TaskStatus.RUNNING, now.plusSeconds(30)),
                new TaskCheckpoint("done", TaskStatus.SUCCEEDED, now.minusSeconds(10)));

        assertThat(new WorkflowRecoveryPlanner().recover(checkpoints, now))
                .containsEntry("expired", TaskStatus.READY)
                .containsEntry("owned", TaskStatus.RUNNING)
                .containsEntry("done", TaskStatus.SUCCEEDED);
    }
}
