package bhavana.agenticsdlc.platform.config;

import bhavana.agenticsdlc.platform.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkpoint8;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "agentic-sdlc.model.provider=deterministic",
        "agentic-sdlc.model.api-key=",
        "agentic-sdlc.repository.approved-root=."
})
class DeterministicApplicationStartupTest {
    @Autowired ModelProvider provider;

    @Test void completeApplicationStartsWithoutApiKeyUsingDeterministicProvider() {
        assertThat(provider).isInstanceOf(DeterministicModelProvider.class);
    }
}
