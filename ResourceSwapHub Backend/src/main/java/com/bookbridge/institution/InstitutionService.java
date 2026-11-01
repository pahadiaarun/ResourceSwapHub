package com.bookbridge.institution;

import java.text.Normalizer;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;

@Service
public class InstitutionService {
    private final InstitutionRepository institutionRepository;
    private final InstitutionSuggestionRepository suggestionRepository;

    public InstitutionService(InstitutionRepository institutionRepository,
                              InstitutionSuggestionRepository suggestionRepository) {
        this.institutionRepository = institutionRepository;
        this.suggestionRepository = suggestionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InstitutionDto> search(String q, String type, String state, String city, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        Page<Institution> page;
        if ((type != null && !type.isBlank()) || (state != null && !state.isBlank()) || (city != null && !city.isBlank())) {
            page = institutionRepository.searchWithFilters(query, type, state, city, pageable);
        } else {
            page = institutionRepository.search(query, pageable);
        }
        return PageResponse.from(page.map(InstitutionDto::from));
    }

    @Transactional(readOnly = true)
    public InstitutionDto get(UUID id) {
        Institution institution = institutionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Institution not found"));
        return InstitutionDto.from(institution);
    }

    @Transactional
    public InstitutionSuggestion submitSuggestion(UUID userId, InstitutionSuggestionRequest request) {
        InstitutionSuggestion suggestion = new InstitutionSuggestion();
        suggestion.setSuggestedBy(userId);
        suggestion.setName(request.name().trim());
        suggestion.setInstitutionType(request.institutionType());
        suggestion.setState(request.state());
        suggestion.setCity(request.city());
        suggestion.setDistrict(request.district());
        suggestion.setAdditionalInfo(request.additionalInfo());
        suggestion.setStatus("PENDING");
        return suggestionRepository.save(suggestion);
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }
}
