package com.bookbridge.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEducationRepository extends JpaRepository<UserEducation, UUID> {

    List<UserEducation> findByUserIdOrderByPrimaryDescStartYearDesc(UUID userId);

    List<UserEducation> findByUserIdAndVisibilityOrderByPrimaryDescStartYearDesc(UUID userId, String visibility);

    Optional<UserEducation> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
