package com.bookbridge.knowledge;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
 * An alias / synonym / abbreviation for a canonical {@link Topic}, so that "DS"
 * resolves to "Data Structures" and "Postgres" to "PostgreSQL".
 */
@Entity
@Table(name = "topic_aliases")
@Getter
@Setter
@NoArgsConstructor
public class TopicAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(nullable = false, length = 120)
    private String alias;

    @Column(name = "normalized_alias", nullable = false, length = 120)
    private String normalizedAlias;

    @Column(name = "alias_type", nullable = false, length = 20)
    private String aliasType = "SYNONYM";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
