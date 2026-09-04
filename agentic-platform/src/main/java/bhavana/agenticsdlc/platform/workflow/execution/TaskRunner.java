package bhavana.agenticsdlc.platform.workflow.execution;

@FunctionalInterface
public interface TaskRunner {
    TaskExecutionResult execute(TaskExecutionContext context) throws Exception;
}

