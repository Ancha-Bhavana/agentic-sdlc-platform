package bhavana.agenticsdlc.platform.workflow.graph;

import java.util.List;

public final class InvalidWorkflowGraphException extends IllegalArgumentException {
    private final List<String> violations;

    public InvalidWorkflowGraphException(List<String> violations) {
        super("Invalid workflow graph: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}

