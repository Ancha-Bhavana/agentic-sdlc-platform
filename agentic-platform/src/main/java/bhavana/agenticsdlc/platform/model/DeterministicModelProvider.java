package bhavana.agenticsdlc.platform.model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
public final class DeterministicModelProvider implements ModelProvider {
    private final ObjectMapper mapper;
    public DeterministicModelProvider(ObjectMapper mapper){this.mapper=mapper;}
    public ModelResponse generate(ModelRequest request,Duration timeout){
        try { String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.inputJson().getBytes(StandardCharsets.UTF_8)));
            var root=JsonNodeFactory.instance.objectNode(); root.put("reasoningSummary","Processed structured inputs for "+request.operation().name());
            root.putArray("assumptions"); root.putArray("decisions").add("Preserve governed workflow boundaries"); root.putArray("risks"); root.putArray("evidence").add("input-sha256:"+hash);
            var artifact=root.putObject("artifact");artifact.put("kind",request.operation().name().toLowerCase());artifact.put("schemaVersion","1.0");artifact.putObject("content").put("inputHash",hash).put("operation",request.operation().name());
            return new ModelResponse("deterministic","offline-v1",mapper.writeValueAsString(root));
        } catch(Exception e){throw new ModelBoundaryException("Deterministic provider failed",e);}
    }
}
