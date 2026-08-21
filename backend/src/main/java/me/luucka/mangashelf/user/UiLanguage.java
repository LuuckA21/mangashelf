package me.luucka.mangashelf.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Languages currently available for the MangaShelf interface. */
public enum UiLanguage {
    IT("it"),
    EN("en");

    private final String code;

    UiLanguage(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static UiLanguage fromCode(String value) {
        if (value == null) return null;
        return UiLanguage.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
