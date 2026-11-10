package com.springsentry.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SampleTestRunner {

	private static final Pattern TEST_SUMMARY = Pattern.compile(
			"Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+), Time elapsed: [^\\r\\n]* -- in ");
	private static final int MAX_OUTPUT_CHARS = 12000;
	private final Path sampleProject;

	public SampleTestRunner(@Value("${springsentry.sample-project-dir}") String sampleProjectDir) {
		this.sampleProject = Path.of(sampleProjectDir).toAbsolutePath().normalize();
	}

	public VerificationResponse runSampleTests() {
		Instant startedAt = Instant.now();
		Path logFile = null;
		Process process = null;
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		String command = windows ? "mvnw.cmd -B test" : "./mvnw -B test";
		try {
			if (!Files.isDirectory(sampleProject) || !Files.exists(sampleProject.resolve("pom.xml"))) {
				return result("INCONCLUSIVE", "Sample project was not found. Extract the full project and run the backend from its folder.",
						"", command, 0, 0, 0, startedAt);
			}
			String[] args = windows
					? new String[] {"cmd.exe", "/c", "mvnw.cmd", "-B", "test"}
					: new String[] {"./mvnw", "-B", "test"};
			logFile = Files.createTempFile("springsentry-sample-test-", ".log");
			process = new ProcessBuilder(args).directory(sampleProject.toFile())
					.redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
			boolean finished = process.waitFor(Duration.ofSeconds(120).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
			long elapsed = Duration.between(startedAt, Instant.now()).toMillis();
			if (!finished) {
				process.destroyForcibly();
				process.waitFor();
				return result("INCONCLUSIVE", "The sample test command exceeded its 120-second time limit.",
						readOutput(logFile), command, 0, 0, elapsed, startedAt);
			}
			String output = readOutput(logFile);
			int[] counts = countTests(output);
			if (process.exitValue() == 0 && counts[0] > 0 && counts[1] == 0 && counts[2] == 0) {
				return result("PASSED", "Bundled sample tests passed. This does not verify submitted code or an AI suggestion.",
						output, command, counts[0], 0, elapsed, startedAt);
			}
			if (process.exitValue() != 0) {
				return result("FAILED", "The bundled sample test command exited with a failure. Review its output.",
						output, command, Math.max(0, counts[0] - counts[1] - counts[2]), counts[1] + counts[2], elapsed, startedAt);
			}
			return result("INCONCLUSIVE", "The command completed, but no test summary was found.",
					output, command, 0, 0, elapsed, startedAt);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			if (process != null) process.destroyForcibly();
			return result("INCONCLUSIVE", "Sample verification was interrupted.", "", command, 0, 0,
				Duration.between(startedAt, Instant.now()).toMillis(), startedAt);
		} catch (IOException exception) {
			return result("INCONCLUSIVE", "Could not start the bundled sample test command. Check Java and file permissions.",
					"", command, 0, 0, Duration.between(startedAt, Instant.now()).toMillis(), startedAt);
		} finally {
			if (logFile != null) {
				try { Files.deleteIfExists(logFile); } catch (IOException ignored) { }
			}
		}
	}

	private static int[] countTests(String output) {
		Matcher matcher = TEST_SUMMARY.matcher(output);
		int run = 0;
		int failures = 0;
		int errors = 0;
		while (matcher.find()) {
			run += Integer.parseInt(matcher.group(1));
			failures += Integer.parseInt(matcher.group(2));
			errors += Integer.parseInt(matcher.group(3));
		}
		return new int[] {run, failures, errors};
	}

	private static String readOutput(Path logFile) throws IOException {
		String output = Files.readString(logFile, StandardCharsets.UTF_8);
		return output.length() <= MAX_OUTPUT_CHARS ? output : output.substring(output.length() - MAX_OUTPUT_CHARS);
	}

	private static VerificationResponse result(String status, String message, String output, String command,
			int passed, int failed, long duration, Instant startedAt) {
		return new VerificationResponse(status, command + " (bundled calculator sample only)",
				passed, failed, duration, output, message, startedAt);
	}
}
