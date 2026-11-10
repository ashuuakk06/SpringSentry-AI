package com.springsentry.analysis;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

	private final AnalysisService analysisService;

	public AnalysisController(AnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	@PostMapping
	public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request, Authentication authentication) {
		return analysisService.analyze(userId(authentication), request);
	}

	@GetMapping
	public List<AnalysisResponse> history(Authentication authentication) {
		return analysisService.history(userId(authentication));
	}

	@GetMapping("/{id}")
	public AnalysisResponse get(@PathVariable UUID id, Authentication authentication) {
		return analysisService.get(userId(authentication), id);
	}

	private static UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}
}
