package com.bookbridge.crypto;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EncryptionSessionRepository extends JpaRepository<EncryptionSession, UUID> {
    Optional<EncryptionSession> findByKeyIdAndActiveTrue(String keyId);
}
