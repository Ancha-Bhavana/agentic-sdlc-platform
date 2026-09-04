package bhavana.agenticsdlc.platform.workflow.coordination;

import bhavana.agenticsdlc.platform.workflow.LifecycleGraphFactory;
import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RevisionImpactPlannerTest {
    @Test void invalidatesChangedStageAndAllDescendantsWhileReusingUnaffectedWork() {
        var graph = new LifecycleGraphFactory().create();
        Map<String, TaskStatus> previous = new HashMap<>();
        graph.tasks().keySet().forEach(id -> previous.put(id, TaskStatus.SUCCEEDED));

        RevisionImpactPlan plan = new RevisionImpactPlanner().plan(graph, previous, Set.of("design"));

        assertThat(plan.reusedTaskIds()).containsExactlyInAnyOrder(
                "understand", "ambiguity", "repository", "decompose");
        assertThat(plan.invalidatedTaskIds()).contains("design", "implementation", "tests",
                "patch-policy", "apply", "validate", "repair", "documentation", "risk", "release");
        assertThat(plan.nextRevisionStatuses().get("decompose")).isEqualTo(TaskStatus.REUSED);
        assertThat(plan.nextRevisionStatuses().get("implementation")).isEqualTo(TaskStatus.PENDING);
    }

    @Test void rejectsUnknownChangeRoots() {
        assertThatThrownBy(() -> new RevisionImpactPlanner().plan(
                new LifecycleGraphFactory().create(), Map.of(), Set.of("unknown")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
