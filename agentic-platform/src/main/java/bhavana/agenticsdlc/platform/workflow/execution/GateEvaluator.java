package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.GateType;
import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;

@FunctionalInterface
public interface GateEvaluator {
    GateDecision evaluate(TaskDefinition task, GateType gate, GatePhase phase);

    static GateEvaluator allowAll() {
        return (task, gate, phase) -> GateDecision.PASS;
    }

    enum GateDecision { PASS, WAIT, REJECT }
    enum GatePhase { ENTRY, EXIT }
}

