package bhavana.agenticsdlc.platform.validation;

public final class FailureClassifier {
    public ValidationResult.FailureType classify(int exitCode, boolean timeout, String output) {
        if (timeout) return ValidationResult.FailureType.TIMEOUT;
        if (exitCode == 0) return ValidationResult.FailureType.NONE;
        String value = output.toLowerCase();
        if (value.contains("compilation failure") || value.contains("cannot find symbol"))
            return ValidationResult.FailureType.COMPILATION;
        if (value.contains("tests run:") || value.contains("there are test failures"))
            return ValidationResult.FailureType.TEST;
        if (value.contains("cannot run program") || value.contains("not recognized"))
            return ValidationResult.FailureType.TOOL;
        return ValidationResult.FailureType.UNKNOWN;
    }
}
