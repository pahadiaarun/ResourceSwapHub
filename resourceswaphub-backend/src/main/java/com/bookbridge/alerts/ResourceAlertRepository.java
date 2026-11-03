package com.bookbridge.alerts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceAlertRepository extends JpaRepository<ResourceAlert, UUID> {

    @EntityGraph(attributePaths = {"category"})
    List<ResourceAlert> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"category"})
    List<ResourceAlert> findByUserIdAndEnabledTrueOrderByCreatedAtDesc(UUID userId);

    Optional<ResourceAlert> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndKeywordAndCategoryIdAndSubjectAndAcademicLevelAndStateIdAndCityIdAndFreeOnly(
            UUID userId, String keyword, UUID categoryId, String subject, String academicLevel,
            UUID stateId, UUID cityId, boolean freeOnly);
}
