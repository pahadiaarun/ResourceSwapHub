package com.bookbridge.location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    Optional<Country> findByIsoCode(String isoCode);

    Optional<Country> findFirstByNormalizedName(String normalizedName);

    List<Country> findByActiveTrueOrderByIsoCodeAsc();

    @Query("select c from Country c where c.active = true and c.normalizedName like concat('%', :q, '%') order by c.name asc")
    List<Country> searchActive(String q);
}
