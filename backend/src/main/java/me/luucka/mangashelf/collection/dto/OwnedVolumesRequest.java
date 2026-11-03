package me.luucka.mangashelf.collection.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OwnedVolumesRequest(@NotNull List<@NotNull Integer> owned) {}
