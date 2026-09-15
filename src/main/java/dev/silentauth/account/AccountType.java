package dev.silentauth.account;

public enum AccountType {

    SESSION("Session"),
    MICROSOFT("Microsoft");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AccountType byName(String name, AccountType fallback) {
        if (name == null) {
            return fallback;
        }
        for (AccountType type : values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        return fallback;
    }
}
