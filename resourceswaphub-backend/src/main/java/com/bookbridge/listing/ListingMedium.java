package com.bookbridge.listing;

/**
 * How the resource is handed over. A catalogue-anchored book copy with no
 * medium set is treated as PHYSICAL by the service layer.
 */
public enum ListingMedium {
    PHYSICAL, DIGITAL_FILE, EXTERNAL_LINK
}
