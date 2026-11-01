package com.bookbridge.location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bookbridge.collection.Collection;
import com.bookbridge.collection.CollectionRepository;
import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;

/**
 * Maps the free-text locations already stored on profiles, listings and collections onto the
 * standardized directory. A row is only touched when its text resolves to exactly one record, an
 * ambiguous or unknown value is reported rather than guessed, and the free-text columns are only
 * re-mirrored once the row resolves — so nothing is lost and a re-run after a bigger import picks
 * up the rows this pass could not place.
 */
@Service
public class LocationBackfillService {

    private static final int BATCH_SIZE = 200;
    private static final int MAX_REPORTED_UNMATCHED = 25;
    private static final UUID START_CURSOR = new UUID(0L, 0L);

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final CollectionRepository collectionRepository;
    private final LocationService locationService;
    private final TransactionTemplate txTemplate;

    public LocationBackfillService(UserRepository userRepository, ListingRepository listingRepository,
                                   CollectionRepository collectionRepository, LocationService locationService,
                                   TransactionTemplate txTemplate) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.collectionRepository = collectionRepository;
        this.locationService = locationService;
        this.txTemplate = txTemplate;
    }

    public LocationBackfillSummary run() {
        long startedAt = System.currentTimeMillis();
        Counters users = backfillUsers();
        // Owners run first: a listing's state is scoped by its seller's standardized location.
        Counters listings = backfillListings();
        Counters collections = backfillCollections();
        List<String> samples = new ArrayList<>(users.unmatched);
        samples.addAll(listings.unmatched);
        samples.addAll(collections.unmatched);
        return new LocationBackfillSummary(
                users.scanned, users.matched,
                listings.scanned, listings.matched,
                collections.scanned, collections.matched,
                samples.size(),
                samples.stream().limit(MAX_REPORTED_UNMATCHED).toList(),
                System.currentTimeMillis() - startedAt);
    }

    private Counters backfillUsers() {
        Counters counters = new Counters();
        forEachCandidate(userRepository::findLocationCandidateIdsAfter, id -> {
            Result outcome = txTemplate.execute(status -> {
                User user = userRepository.findById(id).orElse(null);
                if (user == null) {
                    return Result.skipped();
                }
                LocationService.ResolvedLocation location = locationService.resolve(
                        user.getCountryId(), user.getStateId(), user.getCityId(),
                        user.getCity(), user.getState(), user.getCountry());
                applyLocation(user::setCountryId, user::setStateId, user::setCityId, location);
                mirror(user::setCountry, user::setState, user::setCity, location);
                return new Result(location.cityId() != null,
                        "user " + id + " city='" + user.getCity() + "' state='" + user.getState() + "'");
            });
            counters.record(outcome);
        });
        return counters;
    }

    private Counters backfillListings() {
        Counters counters = new Counters();
        forEachCandidate(listingRepository::findLocationCandidateIdsAfter, id -> {
            Result outcome = txTemplate.execute(status -> {
                Listing listing = listingRepository.findById(id).orElse(null);
                if (listing == null) {
                    return Result.skipped();
                }
                LocationService.ResolvedLocation location = listingLocation(listing.getCity(), listing.getOwner());
                applyLocation(listing::setCountryId, listing::setStateId, listing::setCityId, location);
                mirrorCity(listing::setCity, location);
                return new Result(location.cityId() != null,
                        "listing " + id + " city='" + listing.getCity() + "'");
            });
            counters.record(outcome);
        });
        return counters;
    }

    private Counters backfillCollections() {
        Counters counters = new Counters();
        forEachCandidate(collectionRepository::findLocationCandidateIdsAfter, id -> {
            Result outcome = txTemplate.execute(status -> {
                Collection collection = collectionRepository.findById(id).orElse(null);
                if (collection == null) {
                    return Result.skipped();
                }
                LocationService.ResolvedLocation location = listingLocation(collection.getCity(), collection.getOwner());
                applyLocation(collection::setCountryId, collection::setStateId, collection::setCityId, location);
                mirrorCity(collection::setCity, location);
                return new Result(location.cityId() != null,
                        "collection " + id + " city='" + collection.getCity() + "'");
            });
            counters.record(outcome);
        });
        return counters;
    }

    /** A listing inherits its state from the seller: a city name alone can never identify a place. */
    private LocationService.ResolvedLocation listingLocation(String cityText, User owner) {
        if (owner.getStateId() != null) {
            return locationService.resolve(null, owner.getStateId(), null, cityText, null, null);
        }
        return locationService.resolve(null, null, null, cityText, owner.getState(), owner.getCountry());
    }

    private void applyLocation(Consumer<UUID> country, Consumer<UUID> state, Consumer<UUID> city,
                               LocationService.ResolvedLocation location) {
        if (location.stateId() == null) {
            return;
        }
        country.accept(location.countryId());
        state.accept(location.stateId());
        city.accept(location.cityId());
    }

    /**
     * Keeps the legacy text columns in step with the directory ids so a row can never show a
     * standardized city under a blank or differently spelled state. Nothing is written until the
     * row actually resolves, so unmatched text is preserved exactly as it was.
     */
    private void mirror(Consumer<String> country, Consumer<String> state, Consumer<String> city,
                        LocationService.ResolvedLocation location) {
        if (location.stateId() == null) {
            return;
        }
        country.accept(location.countryName());
        state.accept(location.stateName());
        mirrorCity(city, location);
    }

    private void mirrorCity(Consumer<String> city, LocationService.ResolvedLocation location) {
        if (location.stateId() == null || location.cityName() == null) {
            return;
        }
        city.accept(location.cityName());
    }

    /**
     * Walks the candidates with an id cursor rather than a page offset: a row that gets standardized
     * drops out of the filter, and one that does not must still be stepped over exactly once.
     */
    private void forEachCandidate(CandidateIds candidates, Consumer<UUID> handler) {
        UUID after = START_CURSOR;
        while (true) {
            List<UUID> ids = candidates.after(after, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                return;
            }
            ids.forEach(handler);
            after = ids.get(ids.size() - 1);
        }
    }

    @FunctionalInterface
    private interface CandidateIds {
        List<UUID> after(UUID after, Pageable pageable);
    }

    /** One row's outcome: whether it reached a city, and what to report when it did not. */
    private record Result(boolean cityMatched, String description) {
        static Result skipped() {
            return new Result(false, null);
        }
    }

    private static final class Counters {
        private int scanned;
        private int matched;
        private final List<String> unmatched = new ArrayList<>();

        void record(Result result) {
            if (result == null || result.description() == null) {
                return;
            }
            scanned++;
            if (result.cityMatched()) {
                matched++;
            } else if (unmatched.size() < MAX_REPORTED_UNMATCHED) {
                unmatched.add(result.description());
            }
        }
    }
}
