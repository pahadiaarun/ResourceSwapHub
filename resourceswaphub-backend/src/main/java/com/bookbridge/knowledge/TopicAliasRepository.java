package com.bookbridge.knowledge;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicAliasRepository extends JpaRepository<TopicAlias, UUID> {

    Optional<TopicAlias> findByTopicIdAndNormalizedAlias(UUID topicId, String normalizedAlias);

    Optional<TopicAlias> findFirstByNormalizedAlias(String normalizedAlias);
}
