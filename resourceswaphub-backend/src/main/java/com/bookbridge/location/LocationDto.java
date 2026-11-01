package com.bookbridge.location;

import java.util.UUID;

/**
 * The standardized location attached to a profile, listing or collection. Ids are authoritative;
 * the names come from the directory so the client never has to guess whether a stored free-text
 * value is canonical. A location with no ids is still returned when the legacy text is worth
 * showing (an unmatched city typed before this feature existed).
 */
public record LocationDto(
        UUID countryId,
        UUID stateId,
        UUID cityId,
        String countryName,
        String stateName,
        String cityName
) {
    /**
     * A location that has just been resolved for a write: the ids came from the directory and so do
     * the names, except where text could not be matched, in which case what the user typed is kept
     * visible rather than dropped.
     */
    public static LocationDto from(LocationService.ResolvedLocation resolved) {
        return new LocationDto(
                resolved.countryId(),
                resolved.stateId(),
                resolved.cityId(),
                resolved.countryName(),
                resolved.stateName(),
                resolved.cityName()
        );
    }
}
