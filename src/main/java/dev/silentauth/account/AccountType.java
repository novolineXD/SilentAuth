package dev.silentauth.account;

public enum AccountType {

    SESSION("Session"),
    MICROSOFT("Microsoft"),
    OFFLINE("Offline");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOnline() {
        return this != OFFLINE;
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
