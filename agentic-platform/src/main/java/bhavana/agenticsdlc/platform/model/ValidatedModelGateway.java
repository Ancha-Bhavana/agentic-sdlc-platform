package bhavana.agenticsdlc.platform.model;
import bhavana.agenticsdlc.platform.agent.AgentLimits;
import bhavana.agenticsdlc.platform.agent.AgentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
public final class ValidatedModelGateway {
    private final ModelProvider provider; private final ObjectMapper mapper; private final JsonSchemaResponseValidator validator;
    public ValidatedModelGateway(ModelProvider provider,ObjectMapper mapper){this.provider=provider;this.mapper=mapper;this.validator=new JsonSchemaResponseValidator(mapper);}
    public AgentResult invoke(ModelRequest request,AgentLimits limits){
        if(request.inputJson().getBytes(StandardCharsets.UTF_8).length>limits.maximumContextBytes()) throw new ModelBoundaryException("Agent context exceeds configured size");
        try { var response=provider.generate(request,limits.timeout()); return mapper.treeToValue(validator.validate(response.content(),request.outputSchema(),limits.maximumOutputBytes()),AgentResult.class); }
        catch(ModelBoundaryException e){throw e;} catch(Exception e){throw new ModelBoundaryException("Validated response cannot be decoded",e);}
    }
}
