package me.luucka.mangashelf.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Creates a contiguous range of volumes in one call.
 *
 * <p>A completed run like Berserk Maximum is 21 volumes and One Piece New
 * Edition is past a hundred; entering those one POST at a time is the kind
 * of friction that stops a catalogue from ever being filled in. Numbers that
 * already exist are skipped rather than rejected, so the call can be repeated
 * safely as a run grows.
 */
public record BulkVolumeRequest(

        @NotNull @Min(0) @Max(999) Short from,
        @NotNull @Min(0) @Max(999) Short to
) {
}
