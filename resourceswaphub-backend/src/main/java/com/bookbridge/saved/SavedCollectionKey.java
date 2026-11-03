package com.bookbridge.saved;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for saved_collections. */
public class SavedCollectionKey implements Serializable {
    private UUID user;
    private UUID collection;

    public SavedCollectionKey() {
    }

    public SavedCollectionKey(UUID user, UUID collection) {
        this.user = user;
        this.collection = collection;
    }

    public UUID getUser() {
        return user;
    }

    public void setUser(UUID user) {
        this.user = user;
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
        SavedCollectionKey that = (SavedCollectionKey) other;
        return Objects.equals(user, that.user) && Objects.equals(collection, that.collection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, collection);
    }
}
