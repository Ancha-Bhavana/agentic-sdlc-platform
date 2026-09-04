package bhavana.agenticsdlc.platform.workflow;

import bhavana.agenticsdlc.platform.workflow.domain.*;
import bhavana.agenticsdlc.platform.workflow.graph.WorkflowGraph;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class LifecycleGraphFactory {
    public WorkflowGraph create() {
        return new WorkflowGraph(List.of(
                task("understand", TaskType.REQUIREMENT_UNDERSTANDING, Set.of(), GateType.NONE, GateType.NONE),
                task("ambiguity", TaskType.AMBIGUITY_DETECTION, Set.of("understand"), GateType.NONE, GateType.CLARIFICATION),
                task("repository", TaskType.REPOSITORY_ANALYSIS, Set.of("ambiguity"), GateType.NONE, GateType.NONE),
                task("decompose", TaskType.TASK_DECOMPOSITION, Set.of("repository"), GateType.NONE, GateType.NONE),
                task("design", TaskType.ARCHITECTURE_DESIGN, Set.of("decompose"), GateType.NONE, GateType.CHANGE_APPROVAL),
                task("implementation", TaskType.IMPLEMENTATION, Set.of("design"), GateType.NONE, GateType.NONE),
                task("tests", TaskType.TEST_GENERATION, Set.of("design"), GateType.NONE, GateType.NONE),
                task("patch-policy", TaskType.PATCH_POLICY, Set.of("implementation", "tests"), GateType.ENTRY, GateType.NONE),
                task("apply", TaskType.PATCH_APPLICATION, Set.of("patch-policy"), GateType.NONE, GateType.NONE),
                task("validate", TaskType.VALIDATION, Set.of("apply"), GateType.NONE, GateType.NONE),
                task("repair", TaskType.REPAIR, Set.of("validate"), GateType.NONE, GateType.NONE),
                task("documentation", TaskType.DOCUMENTATION, Set.of("repair"), GateType.NONE, GateType.NONE),
                task("risk", TaskType.SECURITY_RISK_REVIEW, Set.of("repair"), GateType.NONE, GateType.NONE),
                task("release", TaskType.RELEASE_READINESS, Set.of("documentation", "risk"), GateType.NONE, GateType.RELEASE_APPROVAL)));
    }

    private TaskDefinition task(String id, TaskType type, Set<String> dependencies,
                                GateType entry, GateType exit) {
        return new TaskDefinition(id, type, dependencies, entry, exit, 1, Duration.ofMinutes(10));
    }
}
