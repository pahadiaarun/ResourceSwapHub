package com.bookbridge.saved;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.collection.Collection;
import com.bookbridge.collection.CollectionRepository;
import com.bookbridge.collection.CollectionVisibility;
import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingDto;
import com.bookbridge.listing.ListingMapper;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.ListingStatus;
import com.bookbridge.listing.ListingStatusChangedEvent;
import com.bookbridge.notification.NotificationService;
import com.bookbridge.notification.NotificationType;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Saved resources / collections. Everything is private to the saver; saving
 * never creates listings and removing never deletes them.
 */
@Service
public class SavedService {

    private final SavedResourceRepository savedResourceRepository;
    private final SavedCollectionRepository savedCollectionRepository;
    private final ListingRepository listingRepository;
    private final CollectionRepository collectionRepository;
    private final ListingMapper listingMapper;
    private final UserService userService;

    public SavedService(SavedResourceRepository savedResourceRepository,
                        SavedCollectionRepository savedCollectionRepository,
                        ListingRepository listingRepository, CollectionRepository collectionRepository,
                        ListingMapper listingMapper, UserService userService,
                        NotificationService notificationService) {
        this.savedResourceRepository = savedResourceRepository;
        this.savedCollectionRepository = savedCollectionRepository;
        this.listingRepository = listingRepository;
        this.collectionRepository = collectionRepository;
        this.listingMapper = listingMapper;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------------
    // Listing saves
    // ------------------------------------------------------------------------

    @Transactional
    public boolean saveListing(UUID userId, UUID listingId) {
        requireAuth(userId);
        if (savedResourceRepository.findByUserIdAndListingId(userId, listingId).isPresent()) {
            return false; // idempotent re-save
        }
        Listing target = listingRepository.findById(listingId)
                .filter(listing -> listing.getStatus() != ListingStatus.WITHDRAWN)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        SavedResource saved = new SavedResource();
        saved.setUser(userService.require(userId));
        saved.setListing(target);
        savedResourceRepository.save(saved);
        return true;
    }

    @Transactional
    public void unsaveListing(UUID userId, UUID listingId) {
        requireAuth(userId);
        savedResourceRepository.findByUserIdAndListingId(userId, listingId)
                .ifPresent(savedResourceRepository::delete);
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedResourceDto> myListings(UUID userId, UUID categoryId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<SavedResource> result = savedResourceRepository.findForUser(userId, pageable);
        List<SavedResource> visible = result.getContent().stream()
                .filter(saved -> categoryId == null
                        || (saved.getListing().getCategory() != null
                                && saved.getListing().getCategory().getId().equals(categoryId)))
                .toList();
        List<SavedResourceDto> content = visible.stream()
                .map(saved -> SavedResourceDto.from(saved, listingMapper.toDto(saved.getListing())))
                .toList();
        return new PageResponse<>(content, Math.max(0, page), Math.max(1, Math.min(size, 48)),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public boolean isSaved(UUID userId, UUID listingId) {
        return savedResourceRepository.findByUserIdAndListingId(userId, listingId).isPresent();
    }

    // ------------------------------------------------------------------------
    // Collection saves
    // ------------------------------------------------------------------------

    @Transactional
    public boolean saveCollection(UUID userId, UUID collectionId) {
        requireAuth(userId);
        if (savedCollectionRepository.findByUserIdAndCollectionId(userId, collectionId).isPresent()) {
            return false;
        }
        Collection collection = collectionRepository.findWithOwnerById(collectionId)
                .filter(c -> c.getVisibility() == CollectionVisibility.PUBLIC)
                .orElseThrow(() -> ApiException.notFound("That collection could not be found."));
        SavedCollection saved = new SavedCollection();
        saved.setUser(userService.require(userId));
        saved.setCollection(collection);
        savedCollectionRepository.save(saved);
        return true;
    }

    @Transactional
    public void unsaveCollection(UUID userId, UUID collectionId) {
        requireAuth(userId);
        savedCollectionRepository.findByUserIdAndCollectionId(userId, collectionId)
                .ifPresent(savedCollectionRepository::delete);
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedCollectionDto> myCollections(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<SavedCollection> result = savedCollectionRepository.findForUser(userId, pageable);
        List<SavedCollectionDto> content = result.getContent().stream()
                .map(saved -> new SavedCollectionDto(
                        saved.getCollection().getId(),
                        saved.getCollection().getTitle(),
                        saved.getCollection().getVisibility().name(),
                        saved.getCreatedAt()))
                .toList();
        return new PageResponse<>(content, Math.max(0, page), Math.max(1, Math.min(size, 48)),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    // ------------------------------------------------------------------------
    // Lifecycle notifications: a saved resource leaving AVAILABLE
    // ------------------------------------------------------------------------

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onListingStatusChanged(ListingStatusChangedEvent event) {
        if (event.current() == ListingStatus.AVAILABLE) {
            return;
        }
        notificationService.notifySaversUnavailable(savedResourceRepository.findSaverIds(event.listingId()),
                event.listingId());
    }

    private void requireAuth(UUID userId) {
        if (userId == null) {
            throw ApiException.unauthorized("Sign in to save resources.");
        }
    }
}
