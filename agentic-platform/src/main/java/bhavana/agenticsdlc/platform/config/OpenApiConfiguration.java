package bhavana.agenticsdlc.platform.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
    @Bean OpenAPI platformOpenApi() {
        String scheme = "basicAuth";
        return new OpenAPI()
                .info(new Info().title("Agentic SDLC Platform API").version("0.1.0")
                        .description("Authenticated APIs for governed software-engineering workflows"))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
