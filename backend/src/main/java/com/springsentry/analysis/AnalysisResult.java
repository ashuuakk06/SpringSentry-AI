package com.springsentry.analysis;

import java.util.List;

public record AnalysisResult(
		String errorCategory,
		String rootCause,
		String explanation,
		List<String> evidence,
		List<String> debuggingSteps,
		String suggestedFix,
		List<String> testRecommendations) {
}
