package ru.b1sted.altshift.model;

public enum ThemeMode {

    FOLLOW_SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    private final String key;

    ThemeMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ThemeMode fromKey(String key) {
        if (key == null) {
            return FOLLOW_SYSTEM;
        }

        return switch (key) {
            case "light" -> LIGHT;
            case "dark" -> DARK;
            default -> FOLLOW_SYSTEM;
        };
    }
}
