package bhavana.agenticsdlc.platform.validation;

import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Executes only an operator-configured Maven installation, never a repository wrapper. */
public final class AllowlistedProcessRunner implements BuildRunner {
    private final Path executable;
    private final int maximumLogBytes;
    private final FailureClassifier classifier = new FailureClassifier();

    public AllowlistedProcessRunner(Path executable, int maximumLogBytes) {
        if (!executable.isAbsolute() || !Files.isRegularFile(executable) || maximumLogBytes < 1)
            throw new IllegalArgumentException("An absolute trusted Maven executable and positive log bound are required");
        this.executable = executable.normalize();
        this.maximumLogBytes = maximumLogBytes;
    }

    @Override public ValidationResult run(Path workspace, BuildCapability capability, Duration timeout,
                                          CancellationToken token) {
        Objects.requireNonNull(capability);
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Positive timeout required");
        token.throwIfCancelled();
        long start = System.nanoTime();
        Process process = null;
        ExecutorService readers = Executors.newFixedThreadPool(2);
        try {
            for (String configuration : List.of(".mvn/extensions.xml", ".mvn/maven.config", ".mvn/jvm.config")) {
                if (Files.exists(workspace.resolve(configuration)))
                    throw new IllegalArgumentException("Repository Maven execution configuration is not allowed: " + configuration);
            }
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.addAll(capability.arguments());
            ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile());
            builder.environment().keySet().removeIf(key -> !Set.of(
                    "PATH", "JAVA_HOME", "SYSTEMROOT", "WINDIR", "TEMP", "TMP")
                    .contains(key.toUpperCase(Locale.ROOT)));
            process = builder.start();
            Process current = process;
            Future<String> out = readers.submit(() -> read(current.getInputStream()));
            Future<String> err = readers.submit(() -> read(current.getErrorStream()));
            boolean timedOut = false;
            while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                token.throwIfCancelled();
                if (System.nanoTime() - start >= timeout.toNanos()) { timedOut = true; break; }
            }
            if (timedOut) stop(process);
            token.throwIfCancelled();
            String stdout = out.get(5, TimeUnit.SECONDS);
            String stderr = err.get(5, TimeUnit.SECONDS);
            int exit = timedOut ? -1 : process.exitValue();
            return new ValidationResult(capability, exit, timedOut, stdout, stderr,
                    Duration.ofNanos(System.nanoTime() - start),
                    classifier.classify(exit, timedOut, stdout + "\n" + stderr));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Build interrupted");
        } catch (IOException | ExecutionException | TimeoutException e) {
            return new ValidationResult(capability, -1, false, "",
                    "Build tool failed: " + e.getClass().getSimpleName(),
                    Duration.ofNanos(System.nanoTime() - start), ValidationResult.FailureType.TOOL);
        } finally {
            if (process != null) stop(process);
            readers.shutdownNow();
        }
    }

    private void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
        try { process.waitFor(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { process.getInputStream().close(); process.getErrorStream().close(); }
        catch (IOException ignored) { }
    }

    private String read(InputStream stream) throws IOException {
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        boolean truncated = false;
        while ((count = stream.read(buffer)) != -1) {
            int retain = Math.min(count, maximumLogBytes - kept.size());
            kept.write(buffer, 0, retain);
            truncated |= retain < count;
        }
        return kept.toString(StandardCharsets.UTF_8) + (truncated ? "\n[log truncated]" : "");
    }
}
