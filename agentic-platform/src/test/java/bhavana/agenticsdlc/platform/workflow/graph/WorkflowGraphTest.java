package bhavana.agenticsdlc.platform.workflow.graph;

import bhavana.agenticsdlc.platform.workflow.domain.GateType;
import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;
import bhavana.agenticsdlc.platform.workflow.domain.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGraphTest {
    @Test
    void rejectsMissingDependencies() {
        assertThatThrownBy(() -> new WorkflowGraph(List.of(task("implementation", Set.of("design")))))
                .isInstanceOf(InvalidWorkflowGraphException.class)
                .hasMessageContaining("missing dependency design");
    }

    @Test
    void rejectsCycles() {
        assertThatThrownBy(() -> new WorkflowGraph(List.of(
                task("design", Set.of("implementation")),
                task("implementation", Set.of("design")))))
                .isInstanceOf(InvalidWorkflowGraphException.class)
                .hasMessageContaining("Cycle detected");
    }

    @Test
    void exposesIndependentTasksTogetherAndJoinOnlyAfterBothComplete() {
        WorkflowGraph graph = new WorkflowGraph(List.of(
                task("understand", Set.of()),
                task("implementation", Set.of("understand")),
                task("tests", Set.of("understand")),
                task("validate", Set.of("implementation", "tests"))));

        assertThat(graph.readyTaskIds(Map.of())).containsExactly("understand");
        assertThat(graph.readyTaskIds(Map.of("understand", true)))
                .containsExactlyInAnyOrder("implementation", "tests");
        assertThat(graph.readyTaskIds(Map.of("understand", true, "implementation", true)))
                .containsExactly("tests");
        assertThat(graph.readyTaskIds(Map.of(
                "understand", true, "implementation", true, "tests", true)))
                .containsExactly("validate");
    }

    @Test
    void rejectsDuplicateTaskIdentifiers() {
        assertThatThrownBy(() -> new WorkflowGraph(List.of(task("same", Set.of()), task("same", Set.of()))))
                .isInstanceOf(InvalidWorkflowGraphException.class)
                .hasMessageContaining("Duplicate task id");
    }

    private TaskDefinition task(String id, Set<String> dependencies) {
        return new TaskDefinition(id, TaskType.IMPLEMENTATION, dependencies,
                GateType.NONE, GateType.NONE, 2, Duration.ofMinutes(2));
    }
}

