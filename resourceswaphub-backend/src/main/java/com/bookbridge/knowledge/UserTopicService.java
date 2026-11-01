package com.bookbridge.knowledge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;

/**
 * Manages a user's topic relations. Reads are visibility-aware: the owner sees
 * everything, anyone else only sees PUBLIC topics and only when the owner's overall
 * profile is PUBLIC (brief Part 10 §23). Writes are a full replacement so the
 * frontend chip editor can own the whole set.
 */
@Service
public class UserTopicService {

    private static final String DEFAULT_VISIBILITY = "PRIVATE";

    private final UserTopicRepository userTopicRepository;
    private final TopicRepository topicRepository;
    private final TopicService topicService;
    private final UserRepository userRepository;

    public UserTopicService(UserTopicRepository userTopicRepository,
                            TopicRepository topicRepository,
                            TopicService topicService,
                            UserRepository userRepository) {
        this.userTopicRepository = userTopicRepository;
        this.topicRepository = topicRepository;
        this.topicService = topicService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserTopicDto> listFor(UUID viewerId, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        boolean isOwner = viewerId != null && viewerId.equals(ownerId);
        List<UserTopic> relations = isOwner
                ? userTopicRepository.findByUserIdOrderByRelationTypeAsc(ownerId)
                : publicVisible(owner)
                    ? userTopicRepository.findByUserIdAndVisibilityOrderByRelationTypeAsc(ownerId, "PUBLIC")
                    : List.of();
        return toDtos(relations);
    }

    @Transactional
    public List<UserTopicDto> replace(UUID userId, UserTopicsUpdateRequest request) {
        userTopicRepository.deleteByUserId(userId);
        userTopicRepository.flush();

        List<UserTopicsUpdateRequest.Item> items = request == null || request.items() == null
                ? List.of()
                : request.items();

        Set<String> seen = new HashSet<>();
        List<UserTopic> toSave = new ArrayList<>();
        for (UserTopicsUpdateRequest.Item item : items) {
            Topic topic = topicService.resolveOrCreate(item.topicName(), item.topicType(), item.topicId());
            String relation = item.relationType().trim().toUpperCase();
            String dedupeKey = topic.getId() + "|" + relation;
            if (!seen.add(dedupeKey)) {
                continue;
            }
            UserTopic userTopic = new UserTopic();
            userTopic.setUserId(userId);
            userTopic.setTopicId(topic.getId());
            userTopic.setRelationType(relation);
            userTopic.setProficiency(blankToNull(item.proficiency()));
            userTopic.setVisibility(item.visibility() == null || item.visibility().isBlank()
                    ? DEFAULT_VISIBILITY
                    : item.visibility().trim().toUpperCase());
            toSave.add(userTopic);
        }
        userTopicRepository.saveAll(toSave);
        return listFor(userId, userId);
    }

    private List<UserTopicDto> toDtos(List<UserTopic> relations) {
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<UUID> topicIds = relations.stream().map(UserTopic::getTopicId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Topic> topics = topicRepository.findAllById(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, Function.identity()));
        return relations.stream()
                .map(ut -> {
                    Topic topic = topics.get(ut.getTopicId());
                    return new UserTopicDto(
                            ut.getId(),
                            ut.getTopicId(),
                            topic == null ? null : topic.getCanonicalName(),
                            topic == null ? null : topic.getTopicType(),
                            ut.getRelationType(),
                            ut.getProficiency(),
                            ut.getVisibility());
                })
                .toList();
    }

    /**
     * CONNECTIONS_ONLY is treated as private until a connections graph exists, so the
     * platform never over-exposes a profile (conservative default, brief Part 10 §23).
     */
    private boolean publicVisible(User owner) {
        return "PUBLIC".equalsIgnoreCase(owner.getProfileVisibility());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
