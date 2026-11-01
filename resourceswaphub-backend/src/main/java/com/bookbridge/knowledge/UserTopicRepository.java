package com.bookbridge.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTopicRepository extends JpaRepository<UserTopic, UUID> {

    List<UserTopic> findByUserIdOrderByRelationTypeAsc(UUID userId);

    List<UserTopic> findByUserIdAndVisibilityOrderByRelationTypeAsc(UUID userId, String visibility);

    List<UserTopic> findByUserIdAndRelationTypeIn(UUID userId, Collection<String> relationTypes);

    Optional<UserTopic> findByUserIdAndTopicIdAndRelationType(UUID userId, UUID topicId, String relationType);

    void deleteByUserIdAndRelationTypeIn(UUID userId, Collection<String> relationTypes);

    void deleteByUserId(UUID userId);
}
