package com.springsentry.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(nullable = false, unique = true, length = 160)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected UserAccount() {
	}

	public UserAccount(String name, String email, String passwordHash) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public UUID getId() { return id; }
	public String getName() { return name; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public Instant getCreatedAt() { return createdAt; }
}
