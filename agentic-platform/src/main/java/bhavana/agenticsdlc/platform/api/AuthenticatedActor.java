package bhavana.agenticsdlc.platform.api;

import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import org.springframework.security.core.Authentication;

final class AuthenticatedActor {
    private AuthenticatedActor() { }
    static ActorIdentity from(Authentication authentication) {
        String role = authentication.getAuthorities().stream().map(Object::toString).sorted().findFirst()
                .orElseThrow(() -> new SecurityException("Authenticated role required"));
        return new ActorIdentity(authentication.getName(), role);
    }
}
