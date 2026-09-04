package bhavana.agenticsdlc.platform.repository;

import bhavana.agenticsdlc.platform.agent.AgentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PatchProposalDecoderTest {
    private final PatchProposalDecoder decoder = new PatchProposalDecoder(new ObjectMapper());

    @Test void decodesOnlyExplicitStructuredOperations() {
        var result = result(Map.of("operations", List.of(Map.of(
                "type", "CREATE", "path", "src/main/java/Example.java", "content", "class Example {}"))));

        assertThat(decoder.decode(result)).singleElement().satisfies(operation -> {
            assertThat(operation.type()).isEqualTo(FileOperation.Type.CREATE);
            assertThat(operation.path()).isEqualTo("src/main/java/Example.java");
        });
    }

    @Test void rejectsUnknownFieldsAndUpdatesWithoutExpectedHash() {
        assertThatThrownBy(() -> decoder.decode(result(Map.of("operations", List.of(Map.of(
                "type", "CREATE", "path", "src/X.java", "content", "x", "command", "curl"))))))
                .isInstanceOf(RepositorySecurityException.class);
        assertThatThrownBy(() -> decoder.decode(result(Map.of("operations", List.of(Map.of(
                "type", "UPDATE", "path", "src/X.java", "content", "x"))))))
                .isInstanceOf(RepositorySecurityException.class);
    }

    private AgentResult result(Map<String, Object> content) {
        return new AgentResult("summary", List.of(), List.of(), List.of(), List.of(),
                new AgentResult.Artifact("patch", "1.0", content));
    }
}
