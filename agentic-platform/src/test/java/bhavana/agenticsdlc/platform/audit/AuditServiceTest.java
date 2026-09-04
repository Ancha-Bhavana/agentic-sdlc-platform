package bhavana.agenticsdlc.platform.audit;

import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    @Test void hashesOriginalPayloadButRedactsSecretsBeforePersistence() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AuditService service = new AuditService(repository,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        service.record(null, null, "correlation", "CONFIGURATION", new ActorIdentity("operator", "ROLE_OPERATOR"),
                "api_key=super-secret password:letmein");

        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).contains("[REDACTED]").doesNotContain("super-secret", "letmein");
        assertThat(captor.getValue().getPayloadHash()).matches("[0-9a-f]{64}");
    }
}
