package bhavana.agenticsdlc.platform.workflow.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum WorkflowStatus {
    SUBMITTED,
    RUNNING,
    AWAITING_CLARIFICATION,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    REJECTED,
    CANCELLED,
    ROLLED_BACK;

    private static final Map<WorkflowStatus, Set<WorkflowStatus>> ALLOWED_TRANSITIONS = Map.of(
            SUBMITTED, EnumSet.of(RUNNING, CANCELLED, FAILED),
            RUNNING, EnumSet.of(AWAITING_CLARIFICATION, AWAITING_APPROVAL, COMPLETED, FAILED, CANCELLED),
            AWAITING_CLARIFICATION, EnumSet.of(RUNNING, REJECTED, CANCELLED),
            AWAITING_APPROVAL, EnumSet.of(COMPLETED, REJECTED, CANCELLED),
            REJECTED, EnumSet.of(ROLLED_BACK),
            FAILED, EnumSet.of(ROLLED_BACK),
            CANCELLED, EnumSet.of(ROLLED_BACK)
    );

    public boolean isTerminal() {
        return this == COMPLETED || this == ROLLED_BACK;
    }

    public boolean canTransitionTo(WorkflowStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void requireTransitionTo(WorkflowStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidWorkflowTransitionException(this, target);
        }
    }
}

