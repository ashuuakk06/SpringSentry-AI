package com.springsentry.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(min = 2, max = 80) String name,
		@NotBlank @Email @Size(max = 160) String email,
		@NotBlank @Size(min = 8, max = 72) String password) {
}
