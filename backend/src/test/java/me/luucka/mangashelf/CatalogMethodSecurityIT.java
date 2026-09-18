package me.luucka.mangashelf;

import me.luucka.mangashelf.catalog.CatalogService;
import me.luucka.mangashelf.catalog.dto.MangaRequest;
import me.luucka.mangashelf.catalog.dto.SeriesRequest;
import me.luucka.mangashelf.metadata.MetadataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Calls the actual Spring proxies directly, independently of URL authorization. */
class CatalogMethodSecurityIT extends IntegrationTest {
    @Autowired CatalogService catalog;
    @Autowired MetadataService metadata;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsEveryWriteBeforeExecutingIt(boolean signedIn) {
        if (signedIn) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(member, null, member.getAuthorities()));
        } else {
            SecurityContextHolder.clearContext();
        }
        List<Runnable> writes = List.of(
                () -> catalog.createManga(null),
                () -> catalog.updateManga(-1L, null),
                () -> catalog.setCover(-1L, null),
                // Even passing an admin principal must not bypass the caller's role.
                () -> catalog.deleteManga(-1L, admin),
                () -> catalog.createSeries(-1L, null),
                () -> catalog.updateSeries(-1L, null),
                () -> catalog.deleteSeries(-1L, admin),
                () -> metadata.importByAnilistId(-1),
                () -> metadata.search("probe", 1));
        for (Runnable write : writes) {
            assertThatThrownBy(write::run).isInstanceOf(signedIn
                    ? AccessDeniedException.class : AuthenticationCredentialsNotFoundException.class);
        }
    }

    @Test
    void adminCanWriteThroughTheServiceAndMemberCanStillRead() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()));
        var manga = catalog.createManga(new MangaRequest("Method guard", null, null,
                null, null, null, null, null, null, null));
        var series = catalog.createSeries(manga.getId(), new SeriesRequest("Publisher", "Edition", "it", null, false));

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(member, null, member.getAuthorities()));
        assertThat(catalog.getManga(manga.getId()).getTitleRomaji()).isEqualTo("Method guard");
        assertThat(catalog.getSeries(series.getId()).getName()).isEqualTo("Edition");
        assertThat(catalog.listSeries(manga.getId())).hasSize(1);
    }
}
