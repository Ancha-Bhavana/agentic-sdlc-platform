package bhavana.agenticsdlc.platform.governance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GovernancePolicyEngineTest {
    @TempDir Path root;
    private PolicyResultRepository repository;
    private GovernancePolicyEngine policies;

    @BeforeEach void setUp() throws Exception {
        repository = mock(PolicyResultRepository.class);
        policies = new GovernancePolicyEngine(repository, root,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        Files.createDirectories(root.resolve("project"));
    }

    @Test void permitsBoundedCredentialFreeRequirementInApprovedRepository() {
        assertThat(policies.enforceSubmission(UUID.randomUUID(), 1, "Implement analytics", root.resolve("project")))
                .allMatch(PolicyResultEntity::isAllowed);
        verify(repository).saveAll(any());
    }

    @Test void persistsAndBlocksSecretMaterialBeforeWorkflowCreation() {
        assertThatThrownBy(() -> policies.enforceSubmission(UUID.randomUUID(), 1,
                "Use api_key=do-not-store", root.resolve("project")))
                .isInstanceOf(PolicyViolationException.class).hasMessageContaining("secret-material");
        verify(repository).saveAll(any());
    }
}
