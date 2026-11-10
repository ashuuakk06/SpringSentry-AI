package com.springsentry.ai;

import org.springframework.http.HttpStatus;

public class ProviderCallException extends RuntimeException {

	private final HttpStatus httpStatus;
	private final String code;

	public ProviderCallException(HttpStatus httpStatus, String code, String message) {
		super(message);
		this.httpStatus = httpStatus;
		this.code = code;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getCode() {
		return code;
	}
}
