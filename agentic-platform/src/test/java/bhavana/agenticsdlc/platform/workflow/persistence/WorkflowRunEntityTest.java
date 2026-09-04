package bhavana.agenticsdlc.platform.workflow.persistence;

import bhavana.agenticsdlc.platform.workflow.domain.InvalidWorkflowTransitionException;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRunEntityTest {
    @Test
    void appliesValidatedTransitionsAndUpdatesTimestamp() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant started = created.plusSeconds(5);
        WorkflowRunEntity workflow = new WorkflowRunEntity(UUID.randomUUID(), "correlation-1", created);

        workflow.transitionTo(WorkflowStatus.RUNNING, started);

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(workflow.getUpdatedAt()).isEqualTo(started);
    }

    @Test
    void doesNotMutateOnInvalidTransition() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        WorkflowRunEntity workflow = new WorkflowRunEntity(UUID.randomUUID(), "correlation-2", created);

        assertThatThrownBy(() -> workflow.transitionTo(WorkflowStatus.COMPLETED, created.plusSeconds(1)))
                .isInstanceOf(InvalidWorkflowTransitionException.class);
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.SUBMITTED);
        assertThat(workflow.getUpdatedAt()).isEqualTo(created);
    }
}
