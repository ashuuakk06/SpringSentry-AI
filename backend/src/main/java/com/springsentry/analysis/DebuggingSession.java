package com.springsentry.analysis;

import com.springsentry.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "debugging_session")
public class DebuggingSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount owner;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(length = 2000)
	private String description;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String stackTrace;

	@Column(columnDefinition = "TEXT")
	private String sourceCode;

	@Column(nullable = false, length = 160)
	private String errorCategory;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String rootCause;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String explanation;

	@Column(columnDefinition = "TEXT")
	private String evidenceText;

	@Column(columnDefinition = "TEXT")
	private String debuggingStepsText;

	@Column(columnDefinition = "TEXT")
	private String suggestedFix;

	@Column(columnDefinition = "TEXT")
	private String testRecommendationsText;

	@Column(nullable = false, length = 24)
	private String analysisSource;

	@Column(nullable = false, length = 24)
	private String verificationStatus = "NOT_EXECUTED";

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected DebuggingSession() {
	}

	public DebuggingSession(UserAccount owner, AnalysisRequest request, AnalysisResult result, String analysisSource) {
		this.owner = owner;
		this.title = request.title() == null || request.title().isBlank()
				? firstLine(request.stackTrace()) : request.title().trim();
		this.description = request.description();
		this.stackTrace = request.stackTrace().trim();
		this.sourceCode = request.sourceCode() == null ? "" : request.sourceCode();
		this.errorCategory = result.errorCategory();
		this.rootCause = result.rootCause();
		this.explanation = result.explanation();
		this.evidenceText = join(result.evidence());
		this.debuggingStepsText = join(result.debuggingSteps());
		this.suggestedFix = result.suggestedFix();
		this.testRecommendationsText = join(result.testRecommendations());
		this.analysisSource = analysisSource;
	}

	public AnalysisResponse toResponse() {
		return new AnalysisResponse(id, title, description, stackTrace, sourceCode, errorCategory, rootCause,
				explanation, split(evidenceText), split(debuggingStepsText), suggestedFix,
				split(testRecommendationsText), analysisSource, verificationStatus, createdAt);
	}

	public UUID getId() { return id; }
	public UUID getOwnerId() { return owner.getId(); }
	public Instant getCreatedAt() { return createdAt; }

	private static String join(List<String> values) {
		return values == null ? "" : String.join("\n", values);
	}

	private static List<String> split(String value) {
		return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split("\\R"));
	}

	private static String firstLine(String value) {
		String line = value.lines().findFirst().orElse("Java error analysis").trim();
		return line.length() > 120 ? line.substring(0, 117) + "..." : line;
	}
}
