package dev.silentauth.account;

/** Whether a stored token has been proven to still work. */
public enum Validity {

    /** Not checked yet this session. */
    UNKNOWN(""),
    CHECKING("checking"),
    VALID("ok"),
    INVALID("invalid");

    private final String label;

    Validity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
