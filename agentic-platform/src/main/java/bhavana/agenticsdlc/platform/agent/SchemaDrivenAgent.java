package bhavana.agenticsdlc.platform.agent;
import bhavana.agenticsdlc.platform.model.ModelRequest;
import bhavana.agenticsdlc.platform.model.ValidatedModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
public final class SchemaDrivenAgent implements AgentContract {
    private final AgentRole role; private final AgentLimits limits; private final ValidatedModelGateway gateway; private final ObjectMapper mapper;
    public SchemaDrivenAgent(AgentRole role,AgentLimits limits,ValidatedModelGateway gateway,ObjectMapper mapper){this.role=role;this.limits=limits;this.gateway=gateway;this.mapper=mapper;}
    public AgentRole role(){return role;} public AgentLimits limits(){return limits;} public String outputSchema(){return AgentSchemas.RESULT;}
    public AgentResult execute(AgentRequest input){
        try { String json=mapper.writeValueAsString(input); return gateway.invoke(new ModelRequest(role,instruction(role),json,outputSchema(),limits.maximumOutputBytes()),limits); }
        catch(Exception e){ if(e instanceof RuntimeException runtime) throw runtime; throw new IllegalStateException(e); }
    }
    private String instruction(AgentRole role){return "Act as the "+role.name()+" agent. Use only supplied artifacts and repository context. Return schema-valid JSON with concise reasoning summary, assumptions, decisions, risks, evidence, and one versioned artifact. Never request credentials, filesystem access, or command execution.";}
}
