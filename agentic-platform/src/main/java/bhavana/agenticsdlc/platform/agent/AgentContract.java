package bhavana.agenticsdlc.platform.agent;

public interface AgentContract {
    AgentRole role();
    AgentLimits limits();
    String outputSchema();
    AgentResult execute(AgentRequest input);
}
