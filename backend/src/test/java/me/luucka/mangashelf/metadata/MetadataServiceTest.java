package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.catalog.Manga;
import me.luucka.mangashelf.catalog.MangaRepository;
import me.luucka.mangashelf.catalog.PublicationStatus;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.common.CoverStore;
import me.luucka.mangashelf.metadata.dto.AniListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataServiceTest {

    @Mock
    private AniListClient anilist;

    @Mock
    private MangaRepository mangaRepository;

    @Mock
    private CoverStore covers;

    private MetadataService service;

    @BeforeEach
    void setUp() {
        service = new MetadataService(anilist, mangaRepository, covers);
    }

    @Test
    void searchMapsAuthorsAndMarksExistingCatalogueRows() {
        AniListResponse.Media media = media(
                new AniListResponse.Title("Berserk", "Berserk", "ベルセルク"),
                new AniListResponse.CoverImage(null, "https://images.test/large.jpg"));
        Manga existing = mock(Manga.class);
        when(existing.getId()).thenReturn(17L);
        when(anilist.search("Berserk", 10)).thenReturn(List.of(media));
        when(mangaRepository.findByAnilistId(42)).thenReturn(Optional.of(existing));

        var result = service.search("Berserk", 10);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.anilistId()).isEqualTo(42);
            assertThat(row.titleNative()).isEqualTo("ベルセルク");
            assertThat(row.authors()).isEqualTo("Kentaro Miura");
            assertThat(row.coverUrl()).isEqualTo("https://images.test/large.jpg");
            assertThat(row.alreadyInCatalogue()).isTrue();
            assertThat(row.mangaId()).isEqualTo(17L);
        });
    }

    @Test
    void importCreatesAndNormalisesTheCatalogueRow() {
        AniListResponse.Media media = media(
                new AniListResponse.Title("Berserk", "Berserk", "ベルセルク"),
                new AniListResponse.CoverImage("https://images.test/cover.webp", null));
        when(anilist.byId(42)).thenReturn(media);
        when(mangaRepository.findByAnilistId(42)).thenReturn(Optional.empty());
        when(covers.store("https://images.test/cover.webp", "anilist-42"))
                .thenReturn("/covers/anilist-42.webp");
        when(mangaRepository.save(any(Manga.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Manga imported = service.importByAnilistId(42);

        assertThat(imported.getAnilistId()).isEqualTo(42);
        assertThat(imported.getMalId()).isEqualTo(2);
        assertThat(imported.getTitleRomaji()).isEqualTo("Berserk");
        assertThat(imported.getAuthors()).isEqualTo("Kentaro Miura");
        assertThat(imported.getDescription()).isEqualTo("First line\nSecond & final.");
        assertThat(imported.getCoverUrl()).isEqualTo("/covers/anilist-42.webp");
        assertThat(imported.getStatus()).isEqualTo(PublicationStatus.RELEASING);
        assertThat(imported.getGenres()).containsExactly("Action", "Drama");
        assertThat(imported.getStartYear()).isEqualTo((short) 1989);
        assertThat(imported.getTotalVolumes()).isEqualTo((short) 43);
        assertThat(imported.getSyncedAt()).isNotNull();

        ArgumentCaptor<Manga> saved = ArgumentCaptor.forClass(Manga.class);
        verify(mangaRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(imported);
    }

    @Test
    void reimportRefreshesTheSameRowAndKeepsItsLocalCoverWhenNoneIsReturned() {
        Manga existing = new Manga("Old title");
        existing.setAnilistId(42);
        existing.setCoverUrl("/covers/anilist-42.webp");
        AniListResponse.Media media = media(
                new AniListResponse.Title("Updated title", null, null), null);
        when(anilist.byId(42)).thenReturn(media);
        when(mangaRepository.findByAnilistId(42)).thenReturn(Optional.of(existing));
        when(mangaRepository.save(existing)).thenReturn(existing);

        Manga refreshed = service.importByAnilistId(42);

        assertThat(refreshed).isSameAs(existing);
        assertThat(refreshed.getTitleRomaji()).isEqualTo("Updated title");
        assertThat(refreshed.getTitleEnglish()).isNull();
        assertThat(refreshed.getCoverUrl()).isEqualTo("/covers/anilist-42.webp");
        verify(covers, never()).store(any(), any());
    }

    @Test
    void importRejectsMediaWithoutAUsableTitle() {
        AniListResponse.Media media = media(
                new AniListResponse.Title(null, null, "ベルセルク"), null);
        when(anilist.byId(42)).thenReturn(media);
        when(mangaRepository.findByAnilistId(42)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importByAnilistId(42))
                .isInstanceOf(ApiException.class)
                .hasMessage("anilist_media_without_title");
        verify(mangaRepository, never()).save(any());
    }

    private AniListResponse.Media media(AniListResponse.Title title,
                                        AniListResponse.CoverImage cover) {
        return new AniListResponse.Media(
                42,
                2,
                title,
                "First line<br>Second &amp; final.",
                "RELEASING",
                List.of("Action", "Drama"),
                43,
                new AniListResponse.StartDate(1989),
                cover,
                new AniListResponse.Staff(List.of(
                        new AniListResponse.StaffEdge(
                                "Story & Art",
                                new AniListResponse.StaffNode(
                                        new AniListResponse.Name("Kentaro Miura"))),
                        new AniListResponse.StaffEdge(
                                "Translation",
                                new AniListResponse.StaffNode(
                                        new AniListResponse.Name("A Translator"))),
                        new AniListResponse.StaffEdge(
                                "Art Assistant",
                                new AniListResponse.StaffNode(
                                        new AniListResponse.Name("Kentaro Miura")))
                )));
    }
}
