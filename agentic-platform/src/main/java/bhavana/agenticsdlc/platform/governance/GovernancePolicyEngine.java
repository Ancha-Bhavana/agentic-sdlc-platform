package bhavana.agenticsdlc.platform.governance;

import bhavana.agenticsdlc.platform.repository.SafePathResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class GovernancePolicyEngine {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(-----BEGIN .*PRIVATE KEY-----|api[_-]?key\\s*[:=]|password\\s*[:=]|bearer\\s+[a-z0-9._-]+)");
    private final PolicyResultRepository results;
    private final SafePathResolver paths;
    private final Clock clock;

    public GovernancePolicyEngine(PolicyResultRepository results,
                                  @Value("${agentic-sdlc.repository.approved-root:.}") Path approvedRoot,
                                  Clock clock) {
        this.results = results; this.paths = new SafePathResolver(approvedRoot); this.clock = clock;
    }

    public List<PolicyResultEntity> enforceSubmission(UUID workflowId, int revision,
                                                      String requirement, Path repository) {
        List<PolicyResultEntity> decisions = new ArrayList<>();
        decisions.add(decision(workflowId, revision, "requirement-size",
                requirement != null && requirement.getBytes(StandardCharsets.UTF_8).length <= 32_000,
                "Requirement must contain at most 32000 UTF-8 bytes"));
        decisions.add(decision(workflowId, revision, "secret-material",
                requirement != null && !SECRET.matcher(requirement).find(),
                "Requirement must not contain credentials or private keys"));
        boolean admitted;
        try { paths.admitRepository(repository); admitted = true; }
        catch (RuntimeException failure) { admitted = false; }
        decisions.add(decision(workflowId, revision, "approved-repository", admitted,
                "Repository must resolve beneath the configured approved root"));
        results.saveAll(decisions);
        decisions.stream().filter(result -> !result.isAllowed()).findFirst().ifPresent(result -> {
            throw new PolicyViolationException(result.getPolicyName() + ": " + result.getReason());
        });
        return List.copyOf(decisions);
    }

    private PolicyResultEntity decision(UUID workflowId, int revision, String name,
                                        boolean allowed, String rule) {
        return new PolicyResultEntity(workflowId, revision, name, allowed,
                allowed ? "Allowed: " + rule : "Blocked: " + rule, clock.instant());
    }
}
