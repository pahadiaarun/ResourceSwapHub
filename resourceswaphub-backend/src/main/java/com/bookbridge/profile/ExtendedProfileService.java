package com.bookbridge.profile;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;

@Service
public class ExtendedProfileService {
    private final UserRepository userRepository;

    public ExtendedProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ExtendedProfileDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return ExtendedProfileDto.from(user);
    }

    @Transactional
    public ExtendedProfileDto updateProfile(UUID userId, ExtendedProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (request.bio() != null) user.setBio(request.bio());
        if (request.state() != null) user.setState(request.state());
        if (request.country() != null) user.setCountry(request.country());
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
}
