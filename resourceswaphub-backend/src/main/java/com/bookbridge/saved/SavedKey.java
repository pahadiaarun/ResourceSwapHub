package com.bookbridge.saved;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for saved_resources / saved_collections. */
class SavedKey implements Serializable {
    private UUID user;
    private UUID listing;
    private UUID collection;

    public UUID getUser() {
        return user;
    }

    public void setUser(UUID user) {
        this.user = user;
    }

    public UUID getListing() {
        return listing;
    }

    public void setListing(UUID listing) {
        this.listing = listing;
    }

    public UUID getCollection() {
        return collection;
    }

    public void setCollection(UUID collection) {
        this.collection = collection;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SavedKey that = (SavedKey) other;
        return Objects.equals(user, that.user)
                && Objects.equals(listing, that.listing)
                && Objects.equals(collection, that.collection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, listing, collection);
    }
}
