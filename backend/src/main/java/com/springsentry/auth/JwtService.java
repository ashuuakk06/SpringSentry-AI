package com.springsentry.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final SecretKey signingKey;
	private final long expirationHours;

	public JwtService(
			@Value("${springsentry.jwt-secret:}") String secret,
			@Value("${springsentry.jwt-expiration-hours:24}") long expirationHours) {
		byte[] keyBytes = secret == null || secret.isBlank()
				? generateDevelopmentKey()
				: secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalArgumentException("SPRINGSENTRY_JWT_SECRET must contain at least 32 UTF-8 bytes.");
		}
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
		this.expirationHours = expirationHours;
	}

	private static byte[] generateDevelopmentKey() {
		byte[] key = new byte[64];
		new SecureRandom().nextBytes(key);
		return key;
	}

	public String createToken(UUID userId) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId.toString())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
				.signWith(signingKey)
				.compact();
	}

	public UUID getUserId(String token) {
		String subject = Jwts.parser().verifyWith(signingKey).build()
				.parseSignedClaims(token).getPayload().getSubject();
		return UUID.fromString(subject);
	}
}
