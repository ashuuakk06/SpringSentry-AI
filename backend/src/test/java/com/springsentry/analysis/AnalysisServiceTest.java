package com.springsentry.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.springsentry.ai.BharatCodeAiService;
import com.springsentry.ai.ProviderCallException;
import com.springsentry.auth.UserAccount;
import com.springsentry.auth.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.springsentry.analysis.DebuggingSessionRepository;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import org.mockito.Mockito;

class AnalysisServiceTest {

	@Test
	void missingApiKeyUsesDemoAnalysisAndPreservesDemoSourceLabel() {
		UUID userId = UUID.randomUUID();
		UserAccount user = new UserAccount("Student", "student@example.com", "hashed-password");
		DebuggingSessionRepository sessions = Mockito.mock(DebuggingSessionRepository.class);
		UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
		BharatCodeAiService aiService = new BharatCodeAiService(RestClient.builder().build(), new JsonMapper(), "", "qwen-3.8-27b");
		when(users.findById(userId)).thenReturn(Optional.of(user));
		when(sessions.save(any(DebuggingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		AnalysisService analysisService = new AnalysisService(sessions, users, aiService);

		AnalysisResponse response = analysisService.analyze(userId,
				new AnalysisRequest("Demo test", "", "java.lang.NullPointerException", ""));

		assertEquals("DEMO", response.analysisSource());
		assertEquals("NOT_EXECUTED", response.verificationStatus());
		verify(sessions).save(any(DebuggingSession.class));
	}

	@Test
	void providerFailureDoesNotSaveAnAnalysis() {
		UUID userId = UUID.randomUUID();
		DebuggingSessionRepository sessions = Mockito.mock(DebuggingSessionRepository.class);
		UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
		BharatCodeAiService aiService = Mockito.mock(BharatCodeAiService.class);
		when(aiService.isConfigured()).thenReturn(true);
		when(aiService.analyze(any())).thenThrow(new ProviderCallException(HttpStatus.BAD_GATEWAY,
				"AI_RESPONSE_INCOMPLETE", "BharatCode returned an incomplete diagnosis."));
		AnalysisService analysisService = new AnalysisService(sessions, users, aiService);

		assertThrows(ProviderCallException.class, () -> analysisService.analyze(userId,
				new AnalysisRequest("AI test", "", "java.lang.IllegalStateException", "")));

		verify(sessions, never()).save(any(DebuggingSession.class));
		verify(users, never()).findById(userId);
	}

	@Test
	void configuredProviderResultKeepsBharatCodeSourceLabel() {
		UUID userId = UUID.randomUUID();
		UserAccount user = new UserAccount("Student", "student@example.com", "hashed-password");
		DebuggingSessionRepository sessions = Mockito.mock(DebuggingSessionRepository.class);
		UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
		BharatCodeAiService aiService = Mockito.mock(BharatCodeAiService.class);
		when(aiService.isConfigured()).thenReturn(true);
		when(users.findById(userId)).thenReturn(Optional.of(user));
		when(aiService.analyze(any())).thenReturn(new AnalysisResult("IllegalStateException", "A state check failed.",
				"The trace points to the service method.", List.of("Service.java:12"), List.of("Inspect the service state."),
				"Validate the state first.", List.of("Add a regression test.")));
		when(sessions.save(any(DebuggingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		AnalysisService analysisService = new AnalysisService(sessions, users, aiService);

		AnalysisResponse response = analysisService.analyze(userId,
				new AnalysisRequest("AI test", "", "java.lang.IllegalStateException", ""));

		assertEquals("BHARATCODE", response.analysisSource());
	}
}
