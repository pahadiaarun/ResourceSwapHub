package com.bookbridge.knowledge;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's explicit relationship to a canonical topic. The relation type keeps
 * "interested in Java" distinct from "can help with Java" or "experienced in Java"
 * (brief Part 3 §9). Proficiency is an optional self-assessment, never inferred.
 */
@Entity
@Table(name = "user_topics")
@Getter
@Setter
@NoArgsConstructor
public class UserTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "relation_type", nullable = false, length = 20)
    private String relationType;

    @Column(length = 20)
    private String proficiency;

    @Column(nullable = false, length = 20)
    private String visibility = "PRIVATE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
