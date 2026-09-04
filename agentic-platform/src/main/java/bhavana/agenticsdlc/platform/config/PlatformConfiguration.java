package bhavana.agenticsdlc.platform.config;

import bhavana.agenticsdlc.platform.agent.AgentCatalog;
import bhavana.agenticsdlc.platform.model.*;
import bhavana.agenticsdlc.platform.workflow.LifecycleGraphFactory;
import bhavana.agenticsdlc.platform.workflow.coordination.ActiveWorkflowRegistry;
import bhavana.agenticsdlc.platform.workflow.coordination.PersistentWorkflowCoordinator;
import bhavana.agenticsdlc.platform.workflow.graph.WorkflowGraph;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ModelProviderProperties.class)
public class PlatformConfiguration {
    @Bean ObjectMapper modelObjectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean WorkflowGraph workflowGraph() { return new LifecycleGraphFactory().create(); }
    @Bean ActiveWorkflowRegistry activeWorkflowRegistry() { return new ActiveWorkflowRegistry(); }

    @Bean
    ModelProvider modelProvider(ModelProviderProperties properties, ObjectMapper mapper) {
        return switch (properties.provider()) {
            case "deterministic" -> new DeterministicModelProvider(mapper);
            case "openai" -> {
                if (properties.apiKey() == null || properties.apiKey().isBlank())
                    throw new IllegalStateException("OPENAI_API_KEY is required when the OpenAI provider is selected");
                if (properties.endpoint() == null || properties.endpoint().isBlank()
                        || properties.model() == null || properties.model().isBlank())
                    throw new IllegalStateException("OpenAI endpoint and model are required");
                URI endpoint = URI.create(properties.endpoint());
                if (!"https".equalsIgnoreCase(endpoint.getScheme()))
                    throw new IllegalStateException("OpenAI endpoint must use HTTPS");
                yield new HttpModelProvider(HttpClient.newHttpClient(), mapper, endpoint,
                        properties.apiKey(), properties.model());
            }
            default -> throw new IllegalStateException("Unsupported model provider: " + properties.provider());
        };
    }

    @Bean ValidatedModelGateway validatedModelGateway(ModelProvider provider, ObjectMapper mapper) {
        return new ValidatedModelGateway(provider, mapper);
    }

    @Bean AgentCatalog agentCatalog(ValidatedModelGateway gateway, ObjectMapper mapper) {
        return new AgentCatalog(gateway, mapper);
    }

    @Bean
    PersistentWorkflowCoordinator workflowCoordinator(WorkflowRunRepository runs,
            WorkflowRevisionRepository revisions, WorkflowTaskRepository tasks,
            WorkflowGraph graph, ActiveWorkflowRegistry active, Clock clock) {
        return new PersistentWorkflowCoordinator(runs, revisions, tasks, graph, active, clock);
    }
}
