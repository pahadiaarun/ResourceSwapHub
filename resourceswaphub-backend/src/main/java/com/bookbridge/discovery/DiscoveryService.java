package com.bookbridge.discovery;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.catalog.EducationBoard;
import com.bookbridge.listing.BookCondition;
import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingDto;
import com.bookbridge.listing.ListingMapper;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.PriceType;
import com.bookbridge.location.LocationService;
import com.bookbridge.shared.error.ApiException;

@Service
public class DiscoveryService {
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final LocationService locationService;

    public DiscoveryService(ListingRepository listingRepository, ListingMapper listingMapper,
                            LocationService locationService) {
        this.listingRepository = listingRepository;
        this.listingMapper = listingMapper;
        this.locationService = locationService;
    }

    @Transactional(readOnly = true)
    public Page<ListingDto> search(DiscoveryQuery query) {
        int page = Math.max(query.page(), 0);
        int size = Math.max(1, Math.min(query.size(), 48));
        String q = query.q() == null ? "" : query.q().trim();
        boolean hasQuery = !q.isEmpty();
        String tsQuery = hasQuery ? prefixTsQuery(q) : "bookbridge";
        String likeQuery = hasQuery ? "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%" : "%";
        String board = parseBoard(query.board());
        String subject = blankToNull(query.subject());
        String city = blankToNull(query.city());
        String locality = blankToNull(query.locality());
        String priceType = query.priceType() == null ? null : query.priceType().name();
        String condition = query.condition() == null ? null : query.condition().name();

        Page<Listing> results = listingRepository.discover(
                hasQuery,
                tsQuery,
                likeQuery,
                subject,
                query.classLevel(),
                board,
                priceType,
                condition,
                city,
                validated(query.stateId(), locationService::requireState),
                validated(query.cityId(), locationService::requireCity),
                validated(query.districtId(), locationService::requireDistrict),
                locality,
                PageRequest.of(page, size)
        );
        LocationService.LocationIndex index = listingMapper.indexFor(results.getContent());
        return results.map(listing -> listingMapper.toDto(listing, index));
    }

    /** Rejects an unknown location id with the same error the directory API gives, instead of an empty page. */
    private UUID validated(UUID id, Consumer<UUID> require) {
        if (id != null) {
            require.accept(id);
        }
        return id;
    }
    private String prefixTsQuery(String value) {
        String[] tokens = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .trim()
                .split("\\s+");
        StringBuilder builder = new StringBuilder();
        int added = 0;
        for (String token : tokens) {
            if (token.isBlank() || added >= 6) {
                continue;
            }
            String safe = token.length() > 40 ? token.substring(0, 40) : token;
            if (!builder.isEmpty()) {
                builder.append(" & ");
            }
            builder.append(safe).append(":*");
            added++;
        }
        return builder.isEmpty() ? "bookbridge" : builder.toString();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String parseBoard(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EducationBoard.valueOf(value.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_')).name();
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_BOARD", "Choose a supported education board.");
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DiscoveryQuery(
            String q,
            String subject,
            Integer classLevel,
            String board,
            PriceType priceType,
            BookCondition condition,
            String city,
            UUID stateId,
            UUID cityId,
            UUID districtId,
            String locality,
            int page,
            int size
    ) {
    }
}
