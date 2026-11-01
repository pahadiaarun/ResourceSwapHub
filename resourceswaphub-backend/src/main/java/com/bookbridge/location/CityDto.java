package com.bookbridge.location;

import java.util.UUID;

public record CityDto(
        UUID id,
        String name,
        UUID stateId,
        String stateName,
        UUID districtId,
        String districtName,
        String cityType) {

    public static CityDto from(City city, String stateName, String districtName) {
        return new CityDto(
                city.getId(),
                city.getName(),
                city.getStateId(),
                stateName,
                city.getDistrictId(),
                districtName,
                city.getCityType());
    }
}
