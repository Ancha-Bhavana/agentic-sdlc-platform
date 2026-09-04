package bhavana.agenticsdlc.platform.model;
import java.time.Duration;
public interface ModelProvider { ModelResponse generate(ModelRequest request, Duration timeout); }
