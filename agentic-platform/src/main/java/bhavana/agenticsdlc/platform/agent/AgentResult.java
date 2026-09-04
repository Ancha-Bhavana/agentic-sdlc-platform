package bhavana.agenticsdlc.platform.agent;
import java.util.List;
import java.util.Map;
public record AgentResult(String reasoningSummary, List<String> assumptions, List<String> decisions, List<String> risks, List<String> evidence, Artifact artifact) {
    public AgentResult { assumptions=List.copyOf(assumptions); decisions=List.copyOf(decisions); risks=List.copyOf(risks); evidence=List.copyOf(evidence); }
    public record Artifact(String kind, String schemaVersion, Map<String,Object> content) { public Artifact { content=Map.copyOf(content); } }
}
