package bhavana.agenticsdlc.platform.model;
import bhavana.agenticsdlc.platform.agent.AgentRole;
public record ModelRequest(AgentRole operation, String systemInstruction, String inputJson, String outputSchema, int maximumOutputBytes) {}
