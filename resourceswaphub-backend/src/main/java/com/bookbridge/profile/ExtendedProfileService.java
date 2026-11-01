package com.bookbridge.profile;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.location.LocationService;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;
import com.bookbridge.user.UserService;

@Service
public class ExtendedProfileService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final LocationService locationService;

    public ExtendedProfileService(UserRepository userRepository, UserService userService,
                                  LocationService locationService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.locationService = locationService;
    }

    @Transactional(readOnly = true)
    public ExtendedProfileDto getProfile(UUID viewerId, UUID ownerId) {
        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        boolean isOwner = viewerId != null && viewerId.equals(ownerId);
        return isOwner ? ExtendedProfileDto.from(user) : ExtendedProfileDto.publicView(user);
    }

    @Transactional
    public ExtendedProfileDto updateProfile(UUID userId, ExtendedProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (request.bio() != null) user.setBio(request.bio());
        applyLocation(user, request);
        if (request.preferredLanguage() != null) user.setPreferredLanguage(request.preferredLanguage());
        if (request.profileVisibility() != null) user.setProfileVisibility(request.profileVisibility());
        if (request.userType() != null) user.setUserType(request.userType());

        // School
        if (request.schoolName() != null) user.setSchoolName(request.schoolName());
        if (request.schoolLocationState() != null) user.setSchoolLocationState(request.schoolLocationState());
        if (request.schoolLocationCity() != null) user.setSchoolLocationCity(request.schoolLocationCity());
        if (request.schoolLocationDistrict() != null) user.setSchoolLocationDistrict(request.schoolLocationDistrict());
        if (request.schoolClass() != null) user.setSchoolClass(request.schoolClass());
        if (request.schoolStream() != null) user.setSchoolStream(request.schoolStream());
        if (request.schoolBoard() != null) user.setSchoolBoard(request.schoolBoard());
        if (request.schoolAcademicYear() != null) user.setSchoolAcademicYear(request.schoolAcademicYear());
        if (request.schoolExpectedGraduationYear() != null) user.setSchoolExpectedGraduationYear(request.schoolExpectedGraduationYear());

        // College
        if (request.collegeName() != null) user.setCollegeName(request.collegeName());
        if (request.collegeInstitutionType() != null) user.setCollegeInstitutionType(request.collegeInstitutionType());
        if (request.collegeCity() != null) user.setCollegeCity(request.collegeCity());
        if (request.collegeState() != null) user.setCollegeState(request.collegeState());
        if (request.collegeCourse() != null) user.setCollegeCourse(request.collegeCourse());
        if (request.collegeBranch() != null) user.setCollegeBranch(request.collegeBranch());
        if (request.collegeDepartment() != null) user.setCollegeDepartment(request.collegeDepartment());
        if (request.collegeCurrentYearSemester() != null) user.setCollegeCurrentYearSemester(request.collegeCurrentYearSemester());
        if (request.collegeAdmissionYear() != null) user.setCollegeAdmissionYear(request.collegeAdmissionYear());
        if (request.collegeExpectedGraduationYear() != null) user.setCollegeExpectedGraduationYear(request.collegeExpectedGraduationYear());

        // Professional
        if (request.professionalDesignation() != null) user.setProfessionalDesignation(request.professionalDesignation());
        if (request.professionalCompany() != null) user.setProfessionalCompany(request.professionalCompany());
        if (request.professionalIndustry() != null) user.setProfessionalIndustry(request.professionalIndustry());
        if (request.professionalWorkLocation() != null) user.setProfessionalWorkLocation(request.professionalWorkLocation());
        if (request.professionalYearsExperience() != null) user.setProfessionalYearsExperience(request.professionalYearsExperience());
        if (request.professionalHighestQualification() != null) user.setProfessionalHighestQualification(request.professionalHighestQualification());
        if (request.professionalCollegeAttended() != null) user.setProfessionalCollegeAttended(request.professionalCollegeAttended());

        // Teacher
        if (request.teacherInstitutionName() != null) user.setTeacherInstitutionName(request.teacherInstitutionName());
        if (request.teacherInstitutionType() != null) user.setTeacherInstitutionType(request.teacherInstitutionType());
        if (request.teacherSubjects() != null) user.setTeacherSubjects(request.teacherSubjects());
        if (request.teacherClassesTaught() != null) user.setTeacherClassesTaught(request.teacherClassesTaught());
        if (request.teacherYearsExperience() != null) user.setTeacherYearsExperience(request.teacherYearsExperience());
        if (request.teacherAreasExpertise() != null) user.setTeacherAreasExpertise(request.teacherAreasExpertise());
        if (request.teacherCanHelpWith() != null) user.setTeacherCanHelpWith(request.teacherCanHelpWith());

        // Other
        if (request.otherOccupation() != null) user.setOtherOccupation(request.otherOccupation());
        if (request.otherLearningGoals() != null) user.setOtherLearningGoals(request.otherLearningGoals());
        if (request.otherCanContribute() != null) user.setOtherCanContribute(request.otherCanContribute());

        user.setProfileCompletedExtended(true);
        return ExtendedProfileDto.from(userRepository.save(user));
    }

    /**
     * The location fields are partial like the rest of this request: untouched means unchanged, and
     * a typed value that matches the directory is upgraded to the standardized ids.
     */
    private void applyLocation(User user, ExtendedProfileUpdateRequest request) {
        boolean untouched = request.countryId() == null && request.stateId() == null && request.cityId() == null
                && request.state() == null && request.country() == null;
        if (untouched) {
            return;
        }
        String stateText = request.state() != null ? request.state() : user.getState();
        String countryText = request.country() != null ? request.country() : user.getCountry();
        LocationService.ResolvedLocation location = locationService.resolve(request.countryId(),
                request.stateId(), request.cityId(), user.getCity(), stateText, countryText);
        userService.applyLocation(user, location);
        if (location.stateId() == null && request.state() != null) {
            user.setState(request.state());
        }
        if (location.countryId() == null && request.country() != null) {
            user.setCountry(request.country());
        }
    }
}
