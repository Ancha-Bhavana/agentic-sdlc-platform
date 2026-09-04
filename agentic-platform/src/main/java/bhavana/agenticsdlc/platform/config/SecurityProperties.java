package bhavana.agenticsdlc.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agentic-sdlc.security")
public record SecurityProperties(String operatorPassword, String approverPassword, String releasePassword) { }
