package bhavana.agenticsdlc.platform.validation;

import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Runs the admitted workspace's Maven Wrapper with fixed arguments and bounded output. */
public final class WorkspaceMavenBuildRunner implements BuildRunner {
    private final int maximumLogBytes;
    private final FailureClassifier classifier = new FailureClassifier();

    public WorkspaceMavenBuildRunner(int maximumLogBytes) {
        if (maximumLogBytes < 1) throw new IllegalArgumentException("Positive log bound required");
        this.maximumLogBytes = maximumLogBytes;
    }

    @Override public ValidationResult run(Path workspace, BuildCapability capability, Duration timeout,
                                          CancellationToken token) {
        Objects.requireNonNull(workspace); Objects.requireNonNull(capability); Objects.requireNonNull(token);
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Positive timeout required");
        Path root = workspace.toAbsolutePath().normalize();
        Path wrapper = root.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
        if (!Files.isRegularFile(wrapper))
            return toolFailure(capability, "Maven Wrapper is missing");
        for (String configuration : List.of(".mvn/extensions.xml", ".mvn/maven.config", ".mvn/jvm.config"))
            if (Files.exists(root.resolve(configuration)))
                return toolFailure(capability, "Untrusted Maven configuration: " + configuration);

        List<String> command = new ArrayList<>();
        if (isWindows()) command.addAll(List.of("cmd.exe", "/d", "/s", "/c", wrapper.toString()));
        else command.addAll(List.of("/bin/sh", wrapper.toString()));
        command.addAll(capability.arguments());
        long started = System.nanoTime();
        Process process = null;
        ExecutorService readers = Executors.newFixedThreadPool(2);
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile());
            builder.environment().keySet().removeIf(key -> !Set.of(
                    "PATH", "JAVA_HOME", "HOME", "USERPROFILE", "SYSTEMROOT", "WINDIR", "TEMP", "TMP")
                    .contains(key.toUpperCase(Locale.ROOT)));
            process = builder.start();
            Process current = process;
            Future<String> stdout = readers.submit(() -> read(current.getInputStream()));
            Future<String> stderr = readers.submit(() -> read(current.getErrorStream()));
            boolean timedOut = false;
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                token.throwIfCancelled();
                if (System.nanoTime() - started >= timeout.toNanos()) { timedOut = true; break; }
            }
            if (timedOut) stop(process);
            token.throwIfCancelled();
            String out = stdout.get(5, TimeUnit.SECONDS), err = stderr.get(5, TimeUnit.SECONDS);
            int exit = timedOut ? -1 : process.exitValue();
            return new ValidationResult(capability, exit, timedOut, out, err,
                    Duration.ofNanos(System.nanoTime() - started),
                    classifier.classify(exit, timedOut, out + "\n" + err));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Build interrupted");
        } catch (IOException | ExecutionException | TimeoutException failure) {
            return result(capability, -1, false, "", "Build tool failed: " + failure.getClass().getSimpleName(),
                    Duration.ofNanos(System.nanoTime() - started));
        } finally {
            if (process != null) stop(process);
            readers.shutdownNow();
        }
    }

    private ValidationResult result(BuildCapability capability, int exit, boolean timedOut,
                                    String out, String err, Duration duration) {
        return new ValidationResult(capability, exit, timedOut, out, err, duration,
                classifier.classify(exit, timedOut, out + "\n" + err));
    }
    private ValidationResult toolFailure(BuildCapability capability, String message) {
        return new ValidationResult(capability, -1, false, "", message, Duration.ZERO,
                ValidationResult.FailureType.TOOL);
    }
    private String read(InputStream stream) throws IOException {
        ByteArrayOutputStream kept = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
        int count; boolean truncated = false;
        while ((count = stream.read(buffer)) != -1) {
            int retain = Math.min(count, Math.max(0, maximumLogBytes - kept.size()));
            kept.write(buffer, 0, retain); truncated |= retain < count;
        }
        return kept.toString(StandardCharsets.UTF_8) + (truncated ? "\n[log truncated]" : "");
    }
    private void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
    }
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
