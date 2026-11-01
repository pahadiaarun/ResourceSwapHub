package com.bookbridge.crypto;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "encryption_sessions")
@Getter
@Setter
@NoArgsConstructor
public class EncryptionSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_key", nullable = false, length = 64)
    private String sessionKey; // hex encoded AES-256 key

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "client_public_key", length = 500)
    private String clientPublicKey;

    @Column(name = "server_public_key", length = 500)
    private String serverPublicKey;

    @Column(name = "key_id", nullable = false, length = 100)
    private String keyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean active = true;
}
