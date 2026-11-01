package com.bookbridge.location;

import java.util.UUID;

public record DistrictDto(UUID id, UUID stateId, String officialCode, String name) {

    public static DistrictDto from(District district) {
        return new DistrictDto(
                district.getId(),
                district.getStateId(),
                district.getOfficialCode(),
                district.getName());
    }
}
