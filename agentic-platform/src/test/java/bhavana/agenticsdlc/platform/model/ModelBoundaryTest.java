package bhavana.agenticsdlc.platform.model;
import bhavana.agenticsdlc.platform.agent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class ModelBoundaryTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final AgentLimits limits=new AgentLimits(1000,2000,2,Duration.ofSeconds(1));
    @Test void deterministicProviderExercisesEveryAgentContract(){
        var catalog=new AgentCatalog(new ValidatedModelGateway(new DeterministicModelProvider(mapper),mapper),mapper);
        assertThat(catalog.all()).hasSize(12);
        for(AgentRole role:AgentRole.values()) assertThat(catalog.require(role).execute(new AgentRequest("wf",1,"objective",Map.of(),Map.of())).artifact().kind()).isEqualTo(role.name().toLowerCase());
    }
    @Test void rejectsMalformedAndSchemaViolatingOutput(){
        ModelRequest request=new ModelRequest(AgentRole.VALIDATION,"x","{}",AgentSchemas.RESULT,2000);
        assertThatThrownBy(()->new ValidatedModelGateway((r,t)->new ModelResponse("stub","m","not-json"),mapper).invoke(request,limits)).isInstanceOf(ModelBoundaryException.class).hasMessageContaining("valid JSON");
        assertThatThrownBy(()->new ValidatedModelGateway((r,t)->new ModelResponse("stub","m","{}"),mapper).invoke(request,limits)).isInstanceOf(ModelBoundaryException.class).hasMessageContaining("schema");
    }
    @Test void rejectsOversizedContextAndResponse(){
        var gateway=new ValidatedModelGateway((r,t)->new ModelResponse("stub","m","x".repeat(2001)),mapper);
        assertThatThrownBy(()->gateway.invoke(new ModelRequest(AgentRole.REPAIR,"x","x".repeat(1001),AgentSchemas.RESULT,2000),limits)).hasMessageContaining("context");
        assertThatThrownBy(()->gateway.invoke(new ModelRequest(AgentRole.REPAIR,"x","{}",AgentSchemas.RESULT,2000),limits)).hasMessageContaining("response");
    }
}
