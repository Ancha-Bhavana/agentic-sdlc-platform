package bhavana.agenticsdlc.platform.model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.nio.charset.StandardCharsets;
public final class JsonSchemaResponseValidator {
    private final ObjectMapper mapper;
    public JsonSchemaResponseValidator(ObjectMapper mapper){this.mapper=mapper;}
    public JsonNode validate(String content,String schema,int maximumBytes){
        if(content==null||content.getBytes(StandardCharsets.UTF_8).length>maximumBytes) throw new ModelBoundaryException("Model response exceeds configured size");
        try { var value=mapper.readTree(content); var errors=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(schema)).validate(value); if(!errors.isEmpty()) throw new ModelBoundaryException("Model response violates schema: "+errors); return value; }
        catch(ModelBoundaryException e){throw e;} catch(Exception e){throw new ModelBoundaryException("Model response is not valid JSON",e);}
    }
}
