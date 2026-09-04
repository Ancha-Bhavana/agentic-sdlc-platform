package bhavana.agenticsdlc.platform.agent;

import java.time.Duration;

/** Provider-neutral boundary implemented by every specialized engineering agent. */
public interface AgentContract<I, O> {
    String role();
    String schemaVersion();
    Duration timeout();
    O execute(I input);
}

