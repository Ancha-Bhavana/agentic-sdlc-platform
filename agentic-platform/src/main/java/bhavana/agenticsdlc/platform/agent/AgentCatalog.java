package bhavana.agenticsdlc.platform.agent;
import bhavana.agenticsdlc.platform.model.ValidatedModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
public final class AgentCatalog {
    private final Map<AgentRole,AgentContract> agents;
    public AgentCatalog(ValidatedModelGateway gateway,ObjectMapper mapper){
        AgentLimits limits=new AgentLimits(128_000,64_000,3,Duration.ofSeconds(45)); var values=new EnumMap<AgentRole,AgentContract>(AgentRole.class);
        for(AgentRole role:AgentRole.values()) values.put(role,new SchemaDrivenAgent(role,limits,gateway,mapper)); agents=Map.copyOf(values);
    }
    public AgentContract require(AgentRole role){var agent=agents.get(role);if(agent==null)throw new IllegalArgumentException("Unknown agent role");return agent;}
    public Map<AgentRole,AgentContract> all(){return agents;}
}
