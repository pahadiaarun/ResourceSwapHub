package com.bookbridge.profile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.institution.Institution;
import com.bookbridge.institution.InstitutionRepository;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;

@Service
public class EducationService {

    private final UserEducationRepository educationRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;

    public EducationService(UserEducationRepository educationRepository,
                            InstitutionRepository institutionRepository,
                            UserRepository userRepository) {
        this.educationRepository = educationRepository;
        this.institutionRepository = institutionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<EducationDto> listFor(UUID viewerId, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        boolean isOwner = viewerId != null && viewerId.equals(ownerId);
        List<UserEducation> records = isOwner
                ? educationRepository.findByUserIdOrderByPrimaryDescStartYearDesc(ownerId)
                : "PUBLIC".equalsIgnoreCase(owner.getProfileVisibility())
                    ? educationRepository.findByUserIdAndVisibilityOrderByPrimaryDescStartYearDesc(ownerId, "PUBLIC")
                    : List.of();
        return toDtos(records);
    }

    @Transactional
    public EducationDto create(UUID userId, EducationRequest request) {
        UserEducation education = new UserEducation();
        education.setUserId(userId);
        apply(education, request, true);
        return EducationDto.from(educationRepository.save(education), resolveInstitutionName(education.getInstitutionId()));
    }

    @Transactional
    public EducationDto update(UUID userId, UUID id, EducationRequest request) {
        UserEducation education = educationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Education record not found"));
        apply(education, request, false);
        return EducationDto.from(educationRepository.save(education), resolveInstitutionName(education.getInstitutionId()));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        UserEducation education = educationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Education record not found"));
        educationRepository.delete(education);
    }

    /**
     * Applies a request to an entity. On create every provided value is set; on update
     * only non-null values are applied (PATCH semantics). Selecting a canonical
     * institution clears the manual name and vice versa so the two never conflict.
     */
    private void apply(UserEducation education, EducationRequest request, boolean create) {
        if (request.institutionId() != null) {
            education.setInstitutionId(request.institutionId());
            education.setInstitutionNameManual(null);
        } else if (request.institutionNameManual() != null) {
            education.setInstitutionNameManual(trimToNull(request.institutionNameManual()));
            education.setInstitutionId(null);
        } else if (create) {
            education.setInstitutionId(null);
            education.setInstitutionNameManual(null);
        }

        if (request.educationLevel() != null) education.setEducationLevel(trimToNull(request.educationLevel()));
        if (request.degree() != null) education.setDegree(trimToNull(request.degree()));
        if (request.course() != null) education.setCourse(trimToNull(request.course()));
        if (request.branchSpecialization() != null) education.setBranchSpecialization(trimToNull(request.branchSpecialization()));
        if (request.department() != null) education.setDepartment(trimToNull(request.department()));
        if (request.schoolBoard() != null) education.setSchoolBoard(trimToNull(request.schoolBoard()));
        if (request.grade() != null) education.setGrade(trimToNull(request.grade()));
        if (request.academicStream() != null) education.setAcademicStream(trimToNull(request.academicStream()));
        if (request.startYear() != null) education.setStartYear(request.startYear());
        if (request.endYear() != null) education.setEndYear(request.endYear());
        if (request.currentYear() != null) education.setCurrentYear(trimToNull(request.currentYear()));
        if (request.currentSemester() != null) education.setCurrentSemester(trimToNull(request.currentSemester()));
        if (request.expectedCompletionYear() != null) education.setExpectedCompletionYear(request.expectedCompletionYear());
        if (request.academicSession() != null) education.setAcademicSession(trimToNull(request.academicSession()));
        if (request.educationStatus() != null) education.setEducationStatus(request.educationStatus().trim().toUpperCase());
        if (request.current() != null) education.setCurrent(request.current());
        if (request.primary() != null) education.setPrimary(request.primary());
        if (request.gradePercentage() != null) education.setGradePercentage(request.gradePercentage());
        if (request.visibility() != null) education.setVisibility(request.visibility().trim().toUpperCase());
        if (request.description() != null) education.setDescription(trimToNull(request.description()));
    }

    private List<EducationDto> toDtos(List<UserEducation> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<UUID> institutionIds = records.stream()
                .map(UserEducation::getInstitutionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = institutionIds.isEmpty()
                ? Map.of()
                : institutionRepository.findAllById(institutionIds).stream()
                    .collect(Collectors.toMap(Institution::getId, Institution::getName));
        return records.stream()
                .map(e -> EducationDto.from(e, e.getInstitutionId() == null ? null : names.get(e.getInstitutionId())))
                .toList();
    }

    private String resolveInstitutionName(UUID institutionId) {
        return institutionId == null ? null : institutionRepository.findById(institutionId).map(Institution::getName).orElse(null);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
