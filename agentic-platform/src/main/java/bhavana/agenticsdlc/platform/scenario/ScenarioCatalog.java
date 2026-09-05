package bhavana.agenticsdlc.platform.scenario;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public final class ScenarioCatalog {
    private final Map<ScenarioType, ScenarioDefinition> scenarios = Map.of(
            ScenarioType.GREENFIELD, new ScenarioDefinition(ScenarioType.GREENFIELD,
                    "Build a production URL shortener with create, redirect, expiry and analytics APIs",
                    List.of("requirement", "architecture", "implementation", "tests", "validation", "release")),
            ScenarioType.BROWNFIELD, new ScenarioDefinition(ScenarioType.BROWNFIELD,
                    "Add total and daily UTC redirect analytics to the existing URL shortener",
                    List.of("repository-impact", "design", "implementation", "tests", "validation", "release")),
            ScenarioType.AMBIGUOUS, new ScenarioDefinition(ScenarioType.AMBIGUOUS,
                    "Make URL analytics better",
                    List.of("ambiguity", "clarification", "selective-replan", "validation", "release")));
    public Collection<ScenarioDefinition> all() { return scenarios.values().stream().sorted(Comparator.comparing(x -> x.type().name())).toList(); }
    public ScenarioDefinition require(ScenarioType type) { return Optional.ofNullable(scenarios.get(type)).orElseThrow(); }
    public record ScenarioDefinition(ScenarioType type, String requirement, List<String> expectedEvidence) { }
}
