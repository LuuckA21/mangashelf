package me.luucka.mangashelf.user.dto;

import jakarta.validation.constraints.NotNull;
import me.luucka.mangashelf.user.UiLanguage;

public record LanguageRequest(@NotNull UiLanguage language) {
}
