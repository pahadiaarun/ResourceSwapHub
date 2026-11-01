package com.bookbridge.institution;

import java.util.UUID;

public record InstitutionDto(
        UUID id,
        String name,
        String institutionType,
        String officialCode,
        String officialCodeType,
        String state,
        String district,
        String city,
        String source,
        String verificationStatus
) {
    public static InstitutionDto from(Institution institution) {
        return new InstitutionDto(
                institution.getId(),
                institution.getName(),
                institution.getInstitutionType(),
                institution.getOfficialCode(),
                institution.getOfficialCodeType(),
                institution.getState(),
                institution.getDistrict(),
                institution.getCity(),
                institution.getSource(),
                institution.getVerificationStatus()
        );
    }
}
