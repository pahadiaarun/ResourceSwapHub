package com.bookbridge.location;

import java.util.UUID;

public record CountryDto(UUID id, String isoCode, String name) {

    public static CountryDto from(Country country) {
        return new CountryDto(country.getId(), country.getIsoCode(), country.getName());
    }
}
