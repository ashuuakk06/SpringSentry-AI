package com.springsentry.ai;

import com.springsentry.analysis.AnalysisRequest;
import com.springsentry.analysis.AnalysisResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class BharatCodeAiService {

	private static final Logger logger = LoggerFactory.getLogger(BharatCodeAiService.class);
	private static final String INCOMPLETE_CODE = "AI_RESPONSE_INCOMPLETE";

	private final RestClient client;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String model;

	public BharatCodeAiService(
			RestClient client,
			ObjectMapper objectMapper,
			@Value("${bharatcode.api-key:}") String apiKey,
			@Value("${bharatcode.model:qwen-3.8-27b}") String model) {
		this.client = client;
		this.objectMapper = objectMapper;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model;
	}

	public boolean isConfigured() {
		return !apiKey.isBlank();
	}

	public AnalysisResult analyze(AnalysisRequest request) {
		if (!isConfigured()) {
			throw new IllegalStateException("BharatCode analysis was requested without a configured API key.");
		}

		String instructions = "You are SpringSentry AI, an educational Java and Spring Boot debugging assistant. "
				+ "Analyze only the evidence supplied. Never claim a fix is verified. Return one JSON object only with keys: "
				+ "errorCategory (string), rootCause (string), explanation (string), evidence (array of strings), "
				+ "debuggingSteps (array of strings), suggestedFix (string), testRecommendations (array of strings). "
				+ "Keep fixes minimal. If evidence is incomplete, say so explicitly. Do not execute code or tests.";
		String userPrompt = "Description:\n" + safe(request.description()) + "\n\nStack trace:\n" + request.stackTrace()
				+ "\n\nRelevant source code:\n" + safe(request.sourceCode());
		Map<String, Object> payload = Map.of(
				"model", model,
				"messages", List.of(
						Map.of("role", "system", "content", instructions),
						Map.of("role", "user", "content", userPrompt)),
				"max_tokens", 3200);

		JsonNode response = executeProviderRequest(() -> client.post()
				.uri("/chat/completions")
				.header("Authorization", "Bearer " + apiKey)
				.body(payload)
				.retrieve()
				.body(JsonNode.class));
		return parseCompletion(response);
	}

	public ProviderStatus checkConnectivity() {
		var checkedAt = java.time.Instant.now();
		if (!isConfigured()) {
			return new ProviderStatus("DEMO_MODE",
					"No BharatCode API key is configured on the backend. Analyses will use demo mode.", checkedAt);
		}

		try {
			JsonNode response = executeProviderRequest(() -> client.get()
					.uri("/models")
					.header("Authorization", "Bearer " + apiKey)
					.retrieve()
					.body(JsonNode.class));
			JsonNode models = response == null ? null : response.path("data");
			if (models == null || !models.isArray()) {
				logger.warn("BharatCode connectivity check returned an invalid model list");
				return new ProviderStatus("AI_UNAVAILABLE", "BharatCode returned an invalid model list.", checkedAt);
			}
			for (JsonNode availableModel : models) {
				if (model.equals(availableModel.path("id").asText())) {
					return new ProviderStatus("AI_CONNECTED", "BharatCode is reachable and the configured model is available.", checkedAt);
				}
			}
			return new ProviderStatus("AI_UNAVAILABLE",
					"BharatCode is reachable, but the configured model is not available for this key.", checkedAt);
		} catch (ProviderCallException exception) {
			return new ProviderStatus("AI_UNAVAILABLE", exception.getMessage(), checkedAt);
		}
	}

	private JsonNode executeProviderRequest(Supplier<JsonNode> request) {
		try {
			JsonNode response = request.get();
			if (response == null) {
				throw incompleteResponse();
			}
			return response;
		} catch (RestClientResponseException exception) {
			throw translateProviderResponse(exception);
		} catch (ResourceAccessException exception) {
			String message = exception.getMessage();
			if (message != null && (message.toLowerCase(Locale.ROOT).contains("timeout")
					|| message.toLowerCase(Locale.ROOT).contains("timed out"))) {
				logger.warn("BharatCode request timed out");
				throw new ProviderCallException(HttpStatus.GATEWAY_TIMEOUT, "AI_PROVIDER_TIMEOUT",
						"BharatCode did not respond before the timeout. Try again.");
			}
			logger.warn("BharatCode could not be reached");
			throw new ProviderCallException(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE",
					"BharatCode could not be reached. Check the network and try again.");
		} catch (RestClientException exception) {
			logger.warn("BharatCode request failed before a response was received");
			throw new ProviderCallException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_REQUEST_FAILED",
					"BharatCode could not complete the request. Try again shortly.");
		}
	}

	private ProviderCallException translateProviderResponse(RestClientResponseException exception) {
		int status = exception.getStatusCode().value();
		String requestId = exception.getResponseHeaders() == null
				? "unavailable"
				: exception.getResponseHeaders().getFirst("x-request-id");
		if (requestId == null || requestId.isBlank()) requestId = "unavailable";

		if (status == 401 || status == 403) {
			logger.warn("BharatCode rejected backend authentication (status={}, requestId={})", status, requestId);
			return new ProviderCallException(HttpStatus.BAD_GATEWAY, "AI_INVALID_API_KEY",
					"BharatCode rejected the backend API key. Check BHARATCODE_API_KEY.");
		}
		if (status == 429) {
			logger.warn("BharatCode rate limited a request (requestId={})", requestId);
			return new ProviderCallException(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED",
					"BharatCode rate limit reached. Wait a moment and try again.");
		}
		if (status >= 500) {
			logger.warn("BharatCode returned a server error (status={}, requestId={})", status, requestId);
			return new ProviderCallException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
					"BharatCode is temporarily unavailable. Try again shortly.");
		}
		logger.warn("BharatCode rejected a request (status={}, requestId={})", status, requestId);
		return new ProviderCallException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_REQUEST_REJECTED",
				"BharatCode rejected the request. Check the configured model and request format.");
	}

	private AnalysisResult parseCompletion(JsonNode response) {
		JsonNode choices = response.path("choices");
		if (!choices.isArray() || choices.isEmpty()) throw incompleteResponse();

		JsonNode choice = choices.get(0);
		String finishReason = choice.path("finish_reason").asText("");
		if ("length".equals(finishReason)) {
			logger.warn("BharatCode analysis response reached its output limit");
			throw incompleteResponse();
		}
		if (!"stop".equals(finishReason)) {
			logger.warn("BharatCode analysis response did not finish normally (finishReason={})", finishReason);
			throw incompleteResponse();
		}

		String content = choice.path("message").path("content").asText("").trim();
		if (content.isBlank()) throw incompleteResponse();
		try {
			String json = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
			JsonNode node = objectMapper.readTree(json);
			return new AnalysisResult(
					requiredText(node, "errorCategory"),
					requiredText(node, "rootCause"),
					requiredText(node, "explanation"),
					requiredStrings(node.path("evidence")),
					requiredStrings(node.path("debuggingSteps")),
					requiredText(node, "suggestedFix"),
					requiredStrings(node.path("testRecommendations")));
		} catch (ProviderCallException exception) {
			throw exception;
		} catch (Exception exception) {
			logger.warn("BharatCode analysis response was not valid structured JSON");
			throw incompleteResponse();
		}
	}

	private static String requiredText(JsonNode node, String field) {
		String value = node.path(field).asText("").trim();
		if (value.isBlank()) throw incompleteResponse();
		return value;
	}

	private static List<String> requiredStrings(JsonNode array) {
		if (!array.isArray()) throw incompleteResponse();
		List<String> values = new ArrayList<>();
		array.forEach(item -> {
			if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText().trim());
		});
		if (values.isEmpty()) throw incompleteResponse();
		return values;
	}

	private static ProviderCallException incompleteResponse() {
		return new ProviderCallException(HttpStatus.BAD_GATEWAY, INCOMPLETE_CODE,
				"BharatCode returned an incomplete diagnosis. No analysis was saved; retry the request.");
	}

	private static String safe(String value) {
		return value == null || value.isBlank() ? "(not provided)" : value;
	}
}
