package com.bookbridge.location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StateRepository extends JpaRepository<State, UUID> {

    List<State> findByCountryIdAndActiveTrueOrderByNormalizedNameAsc(UUID countryId);

    Optional<State> findFirstByCountryIdAndNormalizedName(UUID countryId, String normalizedName);

    Optional<State> findFirstByCountryIdAndOfficialCode(UUID countryId, String officialCode);

    Optional<State> findByNormalizedName(String normalizedName);
}
