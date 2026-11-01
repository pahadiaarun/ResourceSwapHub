package com.bookbridge.location;

import java.util.UUID;

public record StateDto(UUID id, UUID countryId, String officialCode, String name, String stateType) {

    public static StateDto from(State state) {
        return new StateDto(
                state.getId(),
                state.getCountryId(),
                state.getOfficialCode(),
                state.getName(),
                state.getStateType());
    }
}
