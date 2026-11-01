package com.bookbridge.institution;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionSuggestionRepository extends JpaRepository<InstitutionSuggestion, UUID> {
}
