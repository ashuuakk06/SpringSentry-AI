package com.springsentry.verification;

import java.time.Instant;

public record VerificationResponse(
		String status,
		String command,
		int passedTests,
		int failedTests,
		long durationMillis,
		String output,
		String message,
		Instant executedAt) {
}
