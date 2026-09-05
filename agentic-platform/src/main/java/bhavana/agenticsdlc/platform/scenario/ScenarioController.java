package bhavana.agenticsdlc.platform.scenario;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import java.util.Collection;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private final ScenarioCatalog catalog;
    public ScenarioController(ScenarioCatalog catalog) { this.catalog = catalog; }
    @GetMapping @Operation(summary = "List reproducible deterministic assessment scenarios")
    public Collection<ScenarioCatalog.ScenarioDefinition> list() { return catalog.all(); }
}
