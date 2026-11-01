package com.bookbridge.catalog;

public enum EducationBoard {
    CBSE("CBSE"),
    ICSE("ICSE"),
    IB("IB"),
    STATE_BOARD("State board"),
    OTHER("Other");

    private final String label;

    EducationBoard(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
