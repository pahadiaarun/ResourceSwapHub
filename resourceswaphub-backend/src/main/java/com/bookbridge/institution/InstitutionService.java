package com.bookbridge.institution;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

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
        String query = normalize(q);
        String typeFilter = blankToNull(type);
        String stateFilter = blankToNull(state);
        String cityFilter = blankToNull(city);
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = pageable.getPageSize() <= 0 ? 20 : Math.min(pageable.getPageSize(), 50);

        List<InstitutionDto> content = institutionRepository
                .searchRanked(query, typeFilter, stateFilter, cityFilter, size, page * size)
                .stream()
                .map(InstitutionDto::from)
                .toList();
        long total = institutionRepository.countSearch(query, typeFilter, stateFilter, cityFilter);
        int totalPages = (int) Math.ceil((double) total / size);
        boolean hasNext = page + 1 < totalPages;
        return new PageResponse<>(content, page, size, total, totalPages, hasNext);
    }

    @Transactional(readOnly = true)
    public InstitutionFiltersDto filters() {
        return new InstitutionFiltersDto(institutionRepository.distinctStates(), institutionRepository.distinctTypes());
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
