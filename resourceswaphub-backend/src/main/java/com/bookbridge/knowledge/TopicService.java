package com.bookbridge.knowledge;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.error.ApiException;

/**
 * Owns the canonical knowledge taxonomy: search/autocomplete and the get-or-create
 * resolution used when a user tags themselves with a topic.
 */
@Service
public class TopicService {

    static final int MAX_LIMIT = 25;
    static final int DEFAULT_LIMIT = 10;
    private static final String DEFAULT_TOPIC_TYPE = "SKILL";

    private final TopicRepository topicRepository;
    private final TopicAliasRepository topicAliasRepository;

    public TopicService(TopicRepository topicRepository, TopicAliasRepository topicAliasRepository) {
        this.topicRepository = topicRepository;
        this.topicAliasRepository = topicAliasRepository;
    }

    @Transactional(readOnly = true)
    public List<TopicDto> search(String q, String type, Integer limit) {
        String normalized = normalize(q);
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        if (normalized.isEmpty()) {
            return List.of();
        }
        return topicRepository.search(normalized, blankToNull(type), effectiveLimit)
                .stream()
                .map(TopicDto::from)
                .toList();
    }

    /**
     * Resolves a topic by id, canonical name, or alias; creates a canonical topic when
     * the name is genuinely new so the taxonomy stays extensible without duplicates
     * that differ only by case/whitespace/punctuation.
     */
    @Transactional
    public Topic resolveOrCreate(String name, String type, UUID topicId) {
        if (topicId != null) {
            Optional<Topic> byId = topicRepository.findById(topicId);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("TOPIC_REQUIRED", "A topic name or id is required.");
        }
        String trimmed = name.trim();
        String normalized = normalize(trimmed);
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("TOPIC_INVALID", "Topic name must contain letters or numbers.");
        }
        Optional<Topic> canonical = topicRepository.findByNormalizedName(normalized);
        if (canonical.isPresent()) {
            return canonical.get();
        }
        Optional<Topic> viaAlias = topicAliasRepository.findFirstByNormalizedAlias(normalized)
                .flatMap(alias -> topicRepository.findById(alias.getTopicId()));
        if (viaAlias.isPresent()) {
            return viaAlias.get();
        }
        Topic topic = new Topic();
        topic.setCanonicalName(trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed);
        topic.setNormalizedName(normalized);
        topic.setTopicType(validType(type));
        return topicRepository.save(topic);
    }

    @Transactional
    public TopicDto suggest(TopicSuggestionRequest request) {
        return TopicDto.from(resolveOrCreate(request.name(), request.topicType(), null));
    }

    private String validType(String type) {
        if (type == null || type.isBlank()) {
            return DEFAULT_TOPIC_TYPE;
        }
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "SKILL", "SUBJECT", "TECHNOLOGY", "INTEREST", "EXPERTISE", "LEARNING_GOAL", "EXAM" -> upper;
            default -> throw ApiException.badRequest("TOPIC_TYPE_INVALID", "Unknown topic type: " + type);
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Lowercase, strip diacritics/punctuation, collapse whitespace — matches institution normalization. */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
