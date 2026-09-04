package bhavana.agenticsdlc.platform.workflow.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowStatusTest {
    @Test
    void permitsGovernedHappyPath() {
        assertThat(WorkflowStatus.SUBMITTED.canTransitionTo(WorkflowStatus.RUNNING)).isTrue();
        assertThat(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.AWAITING_APPROVAL)).isTrue();
        assertThat(WorkflowStatus.AWAITING_APPROVAL.canTransitionTo(WorkflowStatus.COMPLETED)).isTrue();
    }

    @Test
    void rejectedWorkflowCanOnlyProceedToRollback() {
        assertThat(WorkflowStatus.REJECTED.canTransitionTo(WorkflowStatus.ROLLED_BACK)).isTrue();
        assertThatThrownBy(() -> WorkflowStatus.REJECTED.requireTransitionTo(WorkflowStatus.RUNNING))
                .isInstanceOf(InvalidWorkflowTransitionException.class);
    }

    @Test
    void completedAndRolledBackStatesAreTerminal() {
        assertThat(WorkflowStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(WorkflowStatus.ROLLED_BACK.isTerminal()).isTrue();
        assertThat(WorkflowStatus.FAILED.isTerminal()).isFalse();
    }
}

