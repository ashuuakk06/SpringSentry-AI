package com.springsentry.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
public class ProviderController {

	private final BharatCodeAiService bharatCodeAiService;

	public ProviderController(BharatCodeAiService bharatCodeAiService) {
		this.bharatCodeAiService = bharatCodeAiService;
	}

	@GetMapping("/status")
	public ProviderStatus status() {
		return bharatCodeAiService.checkConnectivity();
	}
}
