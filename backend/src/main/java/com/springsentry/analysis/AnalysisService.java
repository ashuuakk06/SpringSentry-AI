package com.springsentry.analysis;

import com.springsentry.ai.BharatCodeAiService;
import com.springsentry.auth.UserAccount;
import com.springsentry.auth.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalysisService {

	private final DebuggingSessionRepository sessions;
	private final UserAccountRepository users;
	private final BharatCodeAiService bharatCodeAiService;

	public AnalysisService(DebuggingSessionRepository sessions, UserAccountRepository users,
			BharatCodeAiService bharatCodeAiService) {
		this.sessions = sessions;
		this.users = users;
		this.bharatCodeAiService = bharatCodeAiService;
	}

	public AnalysisResponse analyze(UUID userId, AnalysisRequest request) {
		boolean aiConfigured = bharatCodeAiService.isConfigured();
		AnalysisResult result = aiConfigured ? bharatCodeAiService.analyze(request) : demoAnalysis(request);
		String source = aiConfigured ? "BHARATCODE" : "DEMO";
		UserAccount owner = users.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
		return sessions.save(new DebuggingSession(owner, request, result, source)).toResponse();
	}

	public List<AnalysisResponse> history(UUID userId) {
		return sessions.findTop50ByOwner_IdOrderByCreatedAtDesc(userId).stream()
				.map(DebuggingSession::toResponse).toList();
	}

	public AnalysisResponse get(UUID userId, UUID sessionId) {
		return sessions.findByIdAndOwner_Id(sessionId, userId)
				.map(DebuggingSession::toResponse)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis was not found."));
	}

	private AnalysisResult demoAnalysis(AnalysisRequest request) {
		String stack = request.stackTrace();
		String firstLine = stack.lines().findFirst().orElse("Java exception").trim();
		String category = firstLine.contains(":") ? firstLine.substring(0, firstLine.indexOf(':')).trim() : firstLine;
		boolean nullPointer = stack.contains("NullPointerException");
		String rootCause = nullPointer
				? "A value was null when the application tried to use it. Check the first application frame in the trace."
				: "The stack trace indicates " + category + ". Inspect the first application frame and the values used there.";
		String codeEvidence = request.sourceCode() == null || request.sourceCode().isBlank()
				? "No source code was supplied, so the exact expression could not be confirmed."
				: "Source code was supplied; compare the first application frame with the related method and inputs.";
		String fix = nullPointer
				? "Check the value before dereferencing it. Add validation or a null guard at the point where the value enters the method."
				: "Use the first application frame to find the failing operation, then validate its inputs and add a focused regression test.";
		return new AnalysisResult(category, rootCause,
				"This is a local demo diagnosis because BHARATCODE_API_KEY is not set. It uses simple rules and is not an AI result. "
						+ "Treat it as a starting point and verify any change yourself.",
				List.of("First stack-trace line: " + firstLine, codeEvidence),
				List.of("Open the first application frame in the stack trace.", "Check the input values and state at that line.",
						"Reproduce the error with a focused test before changing code."),
				fix,
				List.of("Add a regression test that reproduces this exception.",
						"Add a test for the relevant boundary or null input.",
						"Run the sample project test command separately; this diagnosis did not execute tests."));
	}
}
