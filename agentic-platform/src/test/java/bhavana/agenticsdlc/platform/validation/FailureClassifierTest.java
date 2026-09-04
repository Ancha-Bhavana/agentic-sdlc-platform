package bhavana.agenticsdlc.platform.validation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {
    private final FailureClassifier classifier = new FailureClassifier();

    @Test void prioritizesTimeoutAndRecognizesRealCompilerAndTestOutput() {
        assertThat(classifier.classify(-1, true, "cannot find symbol"))
                .isEqualTo(ValidationResult.FailureType.TIMEOUT);
        assertThat(classifier.classify(1, false, "COMPILATION FAILURE: cannot find symbol"))
                .isEqualTo(ValidationResult.FailureType.COMPILATION);
        assertThat(classifier.classify(1, false, "Tests run: 2, Failures: 1"))
                .isEqualTo(ValidationResult.FailureType.TEST);
        assertThat(classifier.classify(0, false, ""))
                .isEqualTo(ValidationResult.FailureType.NONE);
    }
}
