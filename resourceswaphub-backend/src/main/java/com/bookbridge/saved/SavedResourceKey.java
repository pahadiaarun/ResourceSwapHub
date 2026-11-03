package com.bookbridge.saved;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for saved_resources. */
public class SavedResourceKey implements Serializable {
    private UUID user;
    private UUID listing;

    public SavedResourceKey() {
    }

    public SavedResourceKey(UUID user, UUID listing) {
        this.user = user;
        this.listing = listing;
    }

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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SavedResourceKey that = (SavedResourceKey) other;
        return Objects.equals(user, that.user) && Objects.equals(listing, that.listing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, listing);
    }
}
