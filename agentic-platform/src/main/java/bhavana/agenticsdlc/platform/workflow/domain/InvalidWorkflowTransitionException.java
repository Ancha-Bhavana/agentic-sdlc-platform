package bhavana.agenticsdlc.platform.workflow.domain;

public final class InvalidWorkflowTransitionException extends IllegalStateException {
    public InvalidWorkflowTransitionException(WorkflowStatus source, WorkflowStatus target) {
        super("Workflow cannot transition from %s to %s".formatted(source, target));
    }
}

