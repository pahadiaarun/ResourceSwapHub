package com.bookbridge.communication;

import java.util.List;

public record MessagePageDto(
        List<MessageDto> messages,
        boolean hasMore,
        Long nextBefore
) {
}
