package com.springsentry.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebuggingSessionRepository extends JpaRepository<DebuggingSession, UUID> {
	List<DebuggingSession> findTop50ByOwner_IdOrderByCreatedAtDesc(UUID ownerId);
	Optional<DebuggingSession> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
