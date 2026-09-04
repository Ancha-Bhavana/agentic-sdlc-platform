package bhavana.agenticsdlc.platform.agent;
import java.util.Map;
public record AgentRequest(String workflowId, int revision, String objective, Map<String,String> upstreamArtifacts, Map<String,String> repositoryContext) {
    public AgentRequest { if (workflowId == null || workflowId.isBlank() || objective == null || objective.isBlank() || revision < 1) throw new IllegalArgumentException("Valid workflow, revision, and objective are required"); upstreamArtifacts=Map.copyOf(upstreamArtifacts); repositoryContext=Map.copyOf(repositoryContext); }
}
