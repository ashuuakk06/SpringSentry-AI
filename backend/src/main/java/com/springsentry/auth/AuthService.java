package com.springsentry.auth;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private final UserAccountRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public AuthResponse register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase();
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
		}
		UserAccount user = users.save(new UserAccount(
				request.name().trim(), email, passwordEncoder.encode(request.password())));
		return responseFor(user);
	}

	public AuthResponse login(LoginRequest request) {
		UserAccount user = users.findByEmailIgnoreCase(request.email().trim())
				.filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."));
		return responseFor(user);
	}

	public AuthResponse currentUser(UUID userId) {
		UserAccount user = users.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
		return new AuthResponse(null, user.getId(), user.getName(), user.getEmail());
	}

	private AuthResponse responseFor(UserAccount user) {
		return new AuthResponse(jwtService.createToken(user.getId()), user.getId(), user.getName(), user.getEmail());
	}
}
