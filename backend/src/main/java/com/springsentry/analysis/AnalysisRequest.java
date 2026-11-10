package com.springsentry.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalysisRequest(
		@Size(max = 120) String title,
		@Size(max = 2000) String description,
		@NotBlank @Size(max = 20000) String stackTrace,
		@Size(max = 30000) String sourceCode) {
}
