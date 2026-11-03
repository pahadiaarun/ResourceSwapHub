package com.bookbridge.shared.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bookbridge.config.AppProperties;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;

@Service
public class MediaService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of("application/pdf", "text/plain");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final Map<String, String> DOCUMENT_EXTENSIONS = Map.of(
            "application/pdf", "pdf",
            "text/plain", "txt"
    );

    private final MediaAssetRepository mediaRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Path storageRoot;

    public MediaService(MediaAssetRepository mediaRepository, UserRepository userRepository,
                        JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.storageRoot = Path.of(properties.storageDir()).toAbsolutePath().normalize();
    }

    @Transactional
    public MediaDto upload(UUID ownerId, MultipartFile file, MediaPurpose purpose) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.unauthorized("Your account could not be found."));
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_REQUIRED", "Choose a file to upload.");
        }
        boolean isDocument = purpose == MediaPurpose.DOCUMENT;
        long limit = isDocument ? MAX_DOCUMENT_BYTES : MAX_BYTES;
        if (file.getSize() > limit) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Files must be 20 MB or smaller.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (isDocument) {
            if (!ALLOWED_DOCUMENT_TYPES.contains(contentType)) {
                throw ApiException.badRequest("UNSUPPORTED_DOCUMENT",
                        "Only PDF and plain-text documents are supported.");
            }
        } else if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("UNSUPPORTED_IMAGE", "Only JPEG, PNG and WebP images are supported.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("UPLOAD_FAILED", "The file could not be read.");
        }
        boolean valid = isDocument
                ? matchesDocumentBytes(contentType, bytes)
                : matchesMagicBytes(contentType, bytes);
        if (!valid) {
            throw isDocument
                    ? ApiException.badRequest("UNSUPPORTED_DOCUMENT", "The file does not look like a valid PDF or text document.")
                    : ApiException.badRequest("UNSUPPORTED_IMAGE", "The file does not look like a valid image.");
        }

        String extension = (isDocument ? DOCUMENT_EXTENSIONS : EXTENSIONS).get(contentType);
        String storageKey = purpose.name().toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID() + "." + extension;
        Path target = storageRoot.resolve(storageKey).normalize();
        if (!target.startsWith(storageRoot)) {
            throw ApiException.badRequest("UPLOAD_FAILED", "Invalid upload target.");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store uploaded image", ex);
        }

        try {
            MediaAsset asset = new MediaAsset();
            asset.setOwner(owner);
            asset.setStorageKey(storageKey);
            asset.setOriginalName(safeName(file.getOriginalFilename()));
            asset.setContentType(contentType);
            asset.setSizeBytes(bytes.length);
            asset.setPurpose(purpose);
            mediaRepository.saveAndFlush(asset);
            return MediaDto.from(asset);
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // A failed cleanup must not mask the original persistence failure.
            }
            throw ex;
        }
    }

    /** Refuses anything but a PDF header or genuinely decodable plain text — no executables, archives or HTML wrappers. */
    private boolean matchesDocumentBytes(String contentType, byte[] bytes) {
        if (bytes.length < 4) {
            return false;
        }
        if ("application/pdf".equals(contentType)) {
            return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        }
        // text/plain: reject any byte sequence that looks binary (NUL or >5% control chars).
        int control = 0;
        for (byte value : bytes) {
            if (value == 0) {
                return false;
            }
            int unsigned = value & 0xFF;
            if (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t') {
                control++;
                if (control > Math.max(2, bytes.length / 20)) {
                    return false;
                }
            }
        }
        return true;
    }

    public MediaAsset getRequired(UUID id) {
        return mediaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That image could not be found."));
    }

    /**
     * Document assets are private: readable only by their owner, or by signed-in
     * users when a live listing references them.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void authorizeDocumentAccess(UUID mediaId, UUID viewerId) {
        MediaAsset asset = getRequired(mediaId);
        if (asset.getOwner() != null && asset.getOwner().getId().equals(viewerId)) {
            return;
        }
        Integer referencingListings = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM listings l "
                        + "LEFT JOIN users u ON u.id = l.owner_id "
                        + "WHERE l.digital_media_id = ? AND l.status = 'AVAILABLE' "
                        + "AND l.moderation_state = 'NONE'", Integer.class, mediaId);
        if (referencingListings == null || referencingListings == 0) {
            throw ApiException.notFound("That file could not be found.");
        }
    }

    public Resource load(MediaAsset asset) {
        Path file = storageRoot.resolve(asset.getStorageKey()).normalize();
        if (!file.startsWith(storageRoot) || !Files.isRegularFile(file)) {
            throw ApiException.notFound("That image could not be found.");
        }
        return new FileSystemResource(file);
    }

    @Transactional
    public void delete(UUID mediaId, UUID requesterId) {
        MediaAsset asset = getRequired(mediaId);
        if (!asset.getOwner().getId().equals(requesterId)) {
            throw ApiException.forbidden("You can only remove images you uploaded.");
        }
        Integer listingUses = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM listing_images WHERE media_id = ?", Integer.class, mediaId);
        Integer avatarUses = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE avatar_url = ?", Integer.class, asset.publicUrl());
        Integer digitalUses = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM listings WHERE digital_media_id = ?", Integer.class, mediaId);
        Integer coverUses = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM collections WHERE cover_media_id = ?", Integer.class, mediaId);
        if ((listingUses != null && listingUses > 0) || (avatarUses != null && avatarUses > 0)
                || (digitalUses != null && digitalUses > 0) || (coverUses != null && coverUses > 0)) {
            throw ApiException.conflict("IMAGE_IN_USE", "This file is in use and cannot be removed.");
        }
        Path file = storageRoot.resolve(asset.getStorageKey()).normalize();
        mediaRepository.delete(asset);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete stored image", ex);
        }
    }

    private boolean matchesMagicBytes(String contentType, byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
            case "image/png" -> (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                    && bytes[3] == 'G';
            case "image/webp" -> bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B'
                    && bytes[11] == 'P';
            default -> false;
        };
    }

    private String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "image";
        }
        String cleaned = original.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }
}
