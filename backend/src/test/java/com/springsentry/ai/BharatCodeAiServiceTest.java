package com.springsentry.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.springsentry.analysis.AnalysisRequest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class BharatCodeAiServiceTest {

	private static final String BASE_URL = "https://bharatcode.ai/api/model/v1";
	private static final String TEST_KEY = "unit-test-key-only";

	@Test
	void successfulCompletionParsesDiagnosisOnlyWhenProviderStoppedNormally() {
		TestClient testClient = createClient(TEST_KEY);
		String diagnosis = "{\"errorCategory\":\"NullPointerException\",\"rootCause\":\"A value was null.\","
				+ "\"explanation\":\"The stack trace points to the order lookup.\",\"evidence\":[\"OrderService.java:42\"],"
				+ "\"debuggingSteps\":[\"Inspect the first application frame.\"],"
				+ "\"suggestedFix\":\"Validate the order before reading its ID.\","
				+ "\"testRecommendations\":[\"Add a null-order regression test.\"]}";
		String response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\""
				+ escapeJson(diagnosis) + "\"}}]}";
		testClient.server().expect(requestTo(BASE_URL + "/chat/completions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer " + TEST_KEY))
				.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

		var result = testClient.service().analyze(request());

		assertEquals("NullPointerException", result.errorCategory());
		assertEquals("A value was null.", result.rootCause());
		assertEquals("Validate the order before reading its ID.", result.suggestedFix());
		testClient.server().verify();
	}

	@Test
	void modelLimitResponseIsRejectedAsIncomplete() {
		TestClient testClient = createClient(TEST_KEY);
		testClient.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}", MediaType.APPLICATION_JSON));

		ProviderCallException exception = assertThrows(ProviderCallException.class,
				() -> testClient.service().analyze(request()));

		assertEquals("AI_RESPONSE_INCOMPLETE", exception.getCode());
		assertEquals(HttpStatus.BAD_GATEWAY, exception.getHttpStatus());
		testClient.server().verify();
	}

	@Test
	void missingDiagnosisFieldsAreRejectedInsteadOfFilledWithDefaults() {
		TestClient testClient = createClient(TEST_KEY);
		String response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"rootCause\\\":\\\"partial\\\"}\"}}]}";
		testClient.server().expect(requestTo(BASE_URL + "/chat/completions"))
				.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

		ProviderCallException exception = assertThrows(ProviderCallException.class,
				() -> testClient.service().analyze(request()));

		assertEquals("AI_RESPONSE_INCOMPLETE", exception.getCode());
		testClient.server().verify();
	}

	@Test
	void invalidKeyErrorIsTranslatedWithoutReturningProviderBodyOrKey() {
		assertProviderError(HttpStatus.UNAUTHORIZED, HttpStatus.BAD_GATEWAY,
				"AI_INVALID_API_KEY", "provider said secret=do-not-return");
	}

	@Test
	void rateLimitIsReturnedAsTooManyRequests() {
		assertProviderError(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS,
				"AI_RATE_LIMITED", "rate limit details");
	}

	@Test
	void providerServerErrorIsReturnedAsGatewayError() {
		assertProviderError(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
				"AI_PROVIDER_ERROR", "internal provider detail");
	}

	@Test
	void requestTimeoutHasGatewayTimeoutStatus() {
		TestClient testClient = createClient(TEST_KEY);
		testClient.server().expect(requestTo(BASE_URL + "/chat/completions"))
				.andRespond(request -> { throw new ResourceAccessException("Read timed out"); });

		ProviderCallException exception = assertThrows(ProviderCallException.class,
				() -> testClient.service().analyze(request()));

		assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getHttpStatus());
		assertEquals("AI_PROVIDER_TIMEOUT", exception.getCode());
		testClient.server().verify();
	}

	@Test
	void connectivityCheckReportsDemoModeWithoutCallingProviderWhenKeyIsMissing() {
		TestClient testClient = createClient("");

		ProviderStatus status = testClient.service().checkConnectivity();

		assertEquals("DEMO_MODE", status.mode());
		assertFalse(status.message().contains(TEST_KEY));
		testClient.server().verify();
	}

	@Test
	void connectivityCheckReportsConnectedOnlyWhenTheConfiguredModelIsListed() {
		TestClient testClient = createClient(TEST_KEY);
		testClient.server().expect(requestTo(BASE_URL + "/models"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"data\":[{\"id\":\"qwen-3.8-27b\"}]}", MediaType.APPLICATION_JSON));

		ProviderStatus status = testClient.service().checkConnectivity();

		assertEquals("AI_CONNECTED", status.mode());
		testClient.server().verify();
	}

	private void assertProviderError(HttpStatus providerStatus, HttpStatus expectedStatus,
			String expectedCode, String providerBody) {
		TestClient testClient = createClient(TEST_KEY);
		testClient.server().expect(requestTo(BASE_URL + "/chat/completions"))
				.andRespond(withStatus(providerStatus).contentType(MediaType.APPLICATION_JSON).body(providerBody));

		ProviderCallException exception = assertThrows(ProviderCallException.class,
				() -> testClient.service().analyze(request()));

		assertEquals(expectedStatus, exception.getHttpStatus());
		assertEquals(expectedCode, exception.getCode());
		assertFalse(exception.getMessage().contains(TEST_KEY));
		assertFalse(exception.getMessage().contains(providerBody));
		testClient.server().verify();
	}

	private TestClient createClient(String key) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		BharatCodeAiService service = new BharatCodeAiService(builder.build(), new JsonMapper(), key, "qwen-3.8-27b");
		return new TestClient(service, server);
	}

	private static AnalysisRequest request() {
		return new AnalysisRequest("Null pointer", "Saving an order failed",
				"java.lang.NullPointerException at OrderService.java:42", "order.getId();");
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private record TestClient(BharatCodeAiService service, MockRestServiceServer server) {
	}
}
