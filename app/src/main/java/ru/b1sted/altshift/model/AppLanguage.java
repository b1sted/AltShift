package ru.b1sted.altshift.model;

import java.util.Locale;

/**
 * Application language choices exposed by the in-app language picker.
 */
public enum AppLanguage {

    FOLLOW_SYSTEM(""),
    ENGLISH("en"),
    RUSSIAN("ru");

    private final String tag;

    AppLanguage(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public static AppLanguage fromTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return FOLLOW_SYSTEM;
        }

        String normalized = tag.replace('_', '-').toLowerCase(Locale.ROOT);

        if (normalized.equals("ru") || normalized.startsWith("ru-")) {
            return RUSSIAN;
        }

        if (normalized.equals("en") || normalized.startsWith("en-")) {
            return ENGLISH;
        }

        return FOLLOW_SYSTEM;
    }
}
