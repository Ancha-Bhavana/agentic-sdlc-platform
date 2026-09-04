package bhavana.agenticsdlc.platform.config;

import bhavana.agenticsdlc.platform.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlatformConfigurationTest {
    private final PlatformConfiguration configuration = new PlatformConfiguration();

    @Test void defaultsToDeterministicProviderWithoutApiKey() {
        ModelProvider provider = configuration.modelProvider(
                new ModelProviderProperties(null, null, null, null), new ObjectMapper());
        assertThat(provider).isInstanceOf(DeterministicModelProvider.class);
    }

    @Test void openAiSelectionRequiresKeyAndHttpsEndpoint() {
        assertThatThrownBy(() -> configuration.modelProvider(
                new ModelProviderProperties("openai", "https://api.openai.com/v1/responses", "gpt", ""),
                new ObjectMapper())).hasMessageContaining("OPENAI_API_KEY");
        assertThatThrownBy(() -> configuration.modelProvider(
                new ModelProviderProperties("openai", "http://example.test", "gpt", "key"),
                new ObjectMapper())).hasMessageContaining("HTTPS");
        assertThat(configuration.modelProvider(
                new ModelProviderProperties("openai", "https://api.openai.com/v1/responses", "gpt", "key"),
                new ObjectMapper())).isInstanceOf(HttpModelProvider.class);
    }
}
