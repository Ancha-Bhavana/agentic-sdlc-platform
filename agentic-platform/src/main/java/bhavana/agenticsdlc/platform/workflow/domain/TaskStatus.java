package bhavana.agenticsdlc.platform.workflow.domain;

public enum TaskStatus {
    PENDING,
    READY,
    RUNNING,
    WAITING_AT_GATE,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    INVALIDATED,
    REUSED;

    public boolean satisfiesDependency() {
        return this == SUCCEEDED || this == REUSED;
    }

    public boolean isFinished() {
        return switch (this) {
            case SUCCEEDED, FAILED, CANCELLED, INVALIDATED, REUSED -> true;
            default -> false;
        };
    }
}

