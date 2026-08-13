package me.luucka.mangashelf.metadata.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shape of the GraphQL reply, mapped only as deeply as the query asks for.
 *
 * <p>Every level is nullable: GraphQL omits fields it could not resolve
 * rather than failing the whole response, so a work without a cover or a
 * start year simply arrives with nulls there.
 *
 * <p>Jackson annotations still live under {@code com.fasterxml.jackson} in
 * Jackson 3; only the databind classes moved to {@code tools.jackson}.
 */
public record AniListResponse(Data data) {

    public record Data(@JsonProperty("Page") PageResult page) {
    }

    public record PageResult(List<Media> media) {
    }

    public record Media(
            Integer id,
            Integer idMal,
            Title title,
            String description,
            String status,
            List<String> genres,
            Integer volumes,
            StartDate startDate,
            CoverImage coverImage,
            Staff staff
    ) {
    }

    /** {@code native} is a Java keyword, hence the mapped name. */
    public record Title(
            String romaji,
            String english,
            @JsonProperty("native") String nativeTitle) {
    }

    public record StartDate(Integer year) {
    }

    /**
     * AniList's size names are off by one from what they suggest: the
     * {@code large} field returns the medium-sized file, so a usable cover
     * means asking for {@code extraLarge} and keeping {@code large} only as
     * a fallback for the rare entry that lacks it.
     */
    public record CoverImage(String extraLarge, String large) {
    }

    public record Staff(List<StaffEdge> edges) {
    }

    public record StaffEdge(String role, StaffNode node) {
    }

    public record StaffNode(Name name) {
    }

    public record Name(String full) {
    }
}
