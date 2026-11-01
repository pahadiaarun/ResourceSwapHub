package com.bookbridge.listing;

public enum BookCondition {
    NEW("New"),
    LIKE_NEW("Like new"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor");

    private final String label;

    BookCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
