package com.springsentry.analysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResponse(
		UUID id,
		String title,
		String description,
		String stackTrace,
		String sourceCode,
		String errorCategory,
		String rootCause,
		String explanation,
		List<String> evidence,
		List<String> debuggingSteps,
		String suggestedFix,
		List<String> testRecommendations,
		String analysisSource,
		String verificationStatus,
		Instant createdAt) {
}
