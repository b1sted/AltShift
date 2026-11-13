package ru.b1sted.altshift.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public final class LocaleControllerTest {

    @Test
    public void matchesIdenticalLocale() {
        assertTrue(LocaleController.matchesLocale(
                Locale.forLanguageTag("ru"), Locale.forLanguageTag("ru")));
    }

    @Test
    public void matchesLocaleWithRegionVariant() {
        assertTrue(LocaleController.matchesLocale(
                Locale.forLanguageTag("ru-RU"), Locale.forLanguageTag("ru")));
        assertTrue(LocaleController.matchesLocale(
                Locale.forLanguageTag("en"), Locale.forLanguageTag("en-US")));
    }

    @Test
    public void rejectsDifferentLanguage() {
        assertFalse(LocaleController.matchesLocale(
                Locale.forLanguageTag("en"), Locale.forLanguageTag("ru")));
    }

    @Test
    public void rejectsMissingLocales() {
        assertFalse(LocaleController.matchesLocale(null, Locale.forLanguageTag("ru")));
        assertFalse(LocaleController.matchesLocale(Locale.forLanguageTag("ru"), null));
    }
}
