package com.bookbridge.listing;

public enum PriceType {
    FREE("Free"),
    FIXED_PRICE("Fixed price"),
    NEGOTIABLE("Negotiable");

    private final String label;

    PriceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
