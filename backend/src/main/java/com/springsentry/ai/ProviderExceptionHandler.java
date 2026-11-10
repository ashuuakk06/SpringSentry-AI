package com.springsentry.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProviderExceptionHandler {

	@ExceptionHandler(ProviderCallException.class)
	public ResponseEntity<ProviderErrorResponse> handleProviderCall(ProviderCallException exception) {
		return ResponseEntity.status(exception.getHttpStatus())
				.body(new ProviderErrorResponse(exception.getCode(), exception.getMessage()));
	}
}
