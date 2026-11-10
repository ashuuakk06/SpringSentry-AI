package com.springsentry.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProviderExceptionHandlerTest {

	@Test
	void exposesSafeStructuredErrorAndProviderStatus() {
		ProviderExceptionHandler handler = new ProviderExceptionHandler();
		ProviderCallException failure = new ProviderCallException(HttpStatus.TOO_MANY_REQUESTS,
				"AI_RATE_LIMITED", "BharatCode rate limit reached. Wait a moment and try again.");

		var response = handler.handleProviderCall(failure);

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
		assertEquals("AI_RATE_LIMITED", response.getBody().code());
		assertEquals(failure.getMessage(), response.getBody().message());
		assertFalse(response.getBody().message().contains("unit-test-key-only"));
	}
}
