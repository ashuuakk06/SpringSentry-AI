package com.springsentry.verification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verifications")
public class VerificationController {

	private final SampleTestRunner sampleTestRunner;

	public VerificationController(SampleTestRunner sampleTestRunner) {
		this.sampleTestRunner = sampleTestRunner;
	}

	@PostMapping("/sample")
	public VerificationResponse runSampleTests() {
		return sampleTestRunner.runSampleTests();
	}
}
