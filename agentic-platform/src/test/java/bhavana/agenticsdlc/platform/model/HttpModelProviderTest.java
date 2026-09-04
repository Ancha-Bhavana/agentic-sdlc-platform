package bhavana.agenticsdlc.platform.model;
import bhavana.agenticsdlc.platform.agent.AgentRole;
import bhavana.agenticsdlc.platform.agent.AgentSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class HttpModelProviderTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void sendsCredentialOnlyAsHeaderAndReturnsStructuredText() throws Exception {
        try(var server=new MockWebServer()){
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ok\\\":true}\"}]}]}"));server.start();
            var provider=new HttpModelProvider(HttpClient.newHttpClient(),mapper,server.url("/v1/responses").uri(),"test-token","test-model");
            var result=provider.generate(request(),Duration.ofSeconds(1));var recorded=server.takeRequest();
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token");
            assertThat(recorded.getBody().readUtf8()).doesNotContain("test-token");
            assertThat(result.content()).isEqualTo("{\"ok\":true}");
        }
    }
    @Test void rejectsProviderErrorsAndMissingOutput() throws Exception {
        try(var server=new MockWebServer()){
            server.enqueue(new MockResponse().setResponseCode(503));server.start();var provider=new HttpModelProvider(HttpClient.newHttpClient(),mapper,server.url("/v1/responses").uri(),null,"m");
            assertThatThrownBy(()->provider.generate(request(),Duration.ofSeconds(1))).hasMessageContaining("HTTP 503");
        }
    }
    private ModelRequest request(){return new ModelRequest(AgentRole.REQUIREMENT_UNDERSTANDING,"instruction","{}",AgentSchemas.RESULT,2000);}
}
