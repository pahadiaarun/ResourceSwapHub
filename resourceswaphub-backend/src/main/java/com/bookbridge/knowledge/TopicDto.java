package com.bookbridge.knowledge;

import java.util.UUID;

public record TopicDto(UUID id, String canonicalName, String topicType) {
    public static TopicDto from(Topic topic) {
        return new TopicDto(topic.getId(), topic.getCanonicalName(), topic.getTopicType());
    }
}
