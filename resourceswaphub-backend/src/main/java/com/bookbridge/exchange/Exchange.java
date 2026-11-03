package com.bookbridge.exchange;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bookbridge.communication.Conversation;
import com.bookbridge.listing.Listing;
import com.bookbridge.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Explicit two-party exchange confirmation on one listing. Both sides confirming
 * moves the listing to EXCHANGED through the listing service so status history
 * and lifecycle rules stay in one place. Messaging remains optional — sitting in
 * a chat never implies an exchange.
 */
@Entity
@Table(name = "exchanges")
@Getter
@Setter
@NoArgsConstructor
public class Exchange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private User participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExchangeStatus status = ExchangeStatus.PENDING;

    @Column(name = "owner_confirmed_at")
    private Instant ownerConfirmedAt;

    @Column(name = "participant_confirmed_at")
    private Instant participantConfirmedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
