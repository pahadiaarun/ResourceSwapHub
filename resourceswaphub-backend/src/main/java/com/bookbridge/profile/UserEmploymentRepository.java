package com.bookbridge.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEmploymentRepository extends JpaRepository<UserEmployment, UUID> {

    List<UserEmployment> findByUserIdOrderByCurrentDescStartDateDesc(UUID userId);

    List<UserEmployment> findByUserIdAndVisibilityOrderByCurrentDescStartDateDesc(UUID userId, String visibility);

    Optional<UserEmployment> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
