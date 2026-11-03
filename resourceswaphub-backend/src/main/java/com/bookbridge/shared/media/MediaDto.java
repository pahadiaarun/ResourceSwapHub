package com.bookbridge.shared.media;

import java.util.UUID;

public record MediaDto(UUID id, String url, String contentType, String originalName, Long sizeBytes) {
    public static MediaDto from(MediaAsset asset) {
        return new MediaDto(asset.getId(), asset.publicUrl(), asset.getContentType(),
                asset.getOriginalName(), asset.getSizeBytes());
    }
}
