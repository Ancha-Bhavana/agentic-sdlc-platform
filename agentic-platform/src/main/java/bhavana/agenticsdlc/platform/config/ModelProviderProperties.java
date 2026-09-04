package bhavana.agenticsdlc.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agentic-sdlc.model")
public record ModelProviderProperties(String provider, String endpoint, String model, String apiKey) {
    public ModelProviderProperties {
        provider = provider == null || provider.isBlank() ? "deterministic" : provider.trim().toLowerCase();
    }
}
