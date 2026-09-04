package bhavana.agenticsdlc.platform.workflow.coordination;

import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import java.util.Map;
import java.util.Set;

public record RevisionImpactPlan(Set<String> invalidatedTaskIds, Set<String> reusedTaskIds,
                                 Map<String, TaskStatus> nextRevisionStatuses) {
    public RevisionImpactPlan {
        invalidatedTaskIds = Set.copyOf(invalidatedTaskIds);
        reusedTaskIds = Set.copyOf(reusedTaskIds);
        nextRevisionStatuses = Map.copyOf(nextRevisionStatuses);
    }
}
