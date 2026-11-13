package ru.b1sted.altshift.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AppLanguageTest {

    @Test
    public void emptyTagsFollowSystem() {
        assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguage.fromTag(null));
        assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguage.fromTag(""));
    }

    @Test
    public void supportedTagsMapToLanguages() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"));
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"));
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTag("ru"));
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTag("ru-RU"));
    }

    @Test
    public void unsupportedTagsFollowSystem() {
        assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguage.fromTag("de"));
        assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguage.fromTag("fr-FR"));
        assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguage.fromTag("unknown"));
    }

    @Test
    public void tagsRoundTrip() {
        for (AppLanguage language : AppLanguage.values()) {
            assertEquals(language, AppLanguage.fromTag(language.tag()));
        }
    }
}
