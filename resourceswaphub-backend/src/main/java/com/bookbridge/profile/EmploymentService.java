package com.bookbridge.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;

@Service
public class EmploymentService {

    private final UserEmploymentRepository employmentRepository;
    private final UserRepository userRepository;

    public EmploymentService(UserEmploymentRepository employmentRepository, UserRepository userRepository) {
        this.employmentRepository = employmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<EmploymentDto> listFor(UUID viewerId, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        boolean isOwner = viewerId != null && viewerId.equals(ownerId);
        List<UserEmployment> records = isOwner
                ? employmentRepository.findByUserIdOrderByCurrentDescStartDateDesc(ownerId)
                : "PUBLIC".equalsIgnoreCase(owner.getProfileVisibility())
                    ? employmentRepository.findByUserIdAndVisibilityOrderByCurrentDescStartDateDesc(ownerId, "PUBLIC")
                    : List.of();
        return records.stream().map(EmploymentDto::from).toList();
    }

    @Transactional
    public EmploymentDto create(UUID userId, EmploymentRequest request) {
        if (isBlank(request.designation())) {
            throw ApiException.badRequest("DESIGNATION_REQUIRED", "A job title / designation is required.");
        }
        UserEmployment employment = new UserEmployment();
        employment.setUserId(userId);
        apply(employment, request, true);
        return EmploymentDto.from(employmentRepository.save(employment));
    }

    @Transactional
    public EmploymentDto update(UUID userId, UUID id, EmploymentRequest request) {
        UserEmployment employment = employmentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Employment record not found"));
        apply(employment, request, false);
        if (isBlank(employment.getDesignation())) {
            throw ApiException.badRequest("DESIGNATION_REQUIRED", "A job title / designation is required.");
        }
        return EmploymentDto.from(employmentRepository.save(employment));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        UserEmployment employment = employmentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Employment record not found"));
        employmentRepository.delete(employment);
    }

    private void apply(UserEmployment employment, EmploymentRequest request, boolean create) {
        if (request.companyName() != null) employment.setCompanyName(trimToNull(request.companyName()));
        if (request.designation() != null) employment.setDesignation(request.designation().trim());
        if (request.industry() != null) employment.setIndustry(trimToNull(request.industry()));
        if (request.employmentType() != null) employment.setEmploymentType(request.employmentType().trim().toUpperCase());
        if (request.workCity() != null) employment.setWorkCity(trimToNull(request.workCity()));
        if (request.workState() != null) employment.setWorkState(trimToNull(request.workState()));
        if (request.startDate() != null) employment.setStartDate(request.startDate());
        if (request.endDate() != null) employment.setEndDate(request.endDate());
        if (request.current() != null) employment.setCurrent(request.current());
        if (request.visibility() != null) employment.setVisibility(request.visibility().trim().toUpperCase());
        if (request.description() != null) employment.setDescription(trimToNull(request.description()));
        if (request.skillsUsed() != null) employment.setSkillsUsed(trimToNull(request.skillsUsed()));
        if (create && employment.getVisibility() == null) employment.setVisibility("PRIVATE");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
