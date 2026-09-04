package bhavana.agenticsdlc.platform.model;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
public final class HttpModelProvider implements ModelProvider {
    private final HttpClient client; private final ObjectMapper mapper; private final URI endpoint; private final String apiKey; private final String model;
    public HttpModelProvider(HttpClient client,ObjectMapper mapper,URI endpoint,String apiKey,String model){this.client=client;this.mapper=mapper;this.endpoint=endpoint;this.apiKey=apiKey;this.model=model;}
    public ModelResponse generate(ModelRequest request,Duration timeout){
        try { var body=mapper.createObjectNode();body.put("model",model);body.put("instructions",request.systemInstruction());body.put("input",request.inputJson());body.put("max_output_tokens",Math.max(1,request.maximumOutputBytes()/4));
            var format=body.putObject("text").putObject("format");format.put("type","json_schema");format.put("name","agent_result");format.put("strict",true);format.set("schema",mapper.readTree(request.outputSchema()));
            var builder=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json");if(apiKey!=null&&!apiKey.isBlank())builder.header("Authorization","Bearer "+apiKey);
            var response=client.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)throw new ModelBoundaryException("Model provider returned HTTP "+response.statusCode());
            var json=mapper.readTree(response.body());String text=extractOutputText(json);if(text==null)throw new ModelBoundaryException("Provider response is missing output text");
            return new ModelResponse("http",model,text);
        } catch(ModelBoundaryException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ModelBoundaryException("Model call interrupted",e);}catch(Exception e){throw new ModelBoundaryException("Model call failed",e);}
    }
    private String extractOutputText(com.fasterxml.jackson.databind.JsonNode root){
        var direct=root.get("output_text");if(direct!=null&&direct.isTextual())return direct.asText();
        var output=root.path("output");if(output.isArray())for(var item:output)for(var content:item.path("content"))if("output_text".equals(content.path("type").asText())&&content.path("text").isTextual())return content.path("text").asText();
        return null;
    }
}
