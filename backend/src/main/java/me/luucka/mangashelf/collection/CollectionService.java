package me.luucka.mangashelf.collection;

import me.luucka.mangashelf.catalog.CatalogService;
import me.luucka.mangashelf.catalog.VolumeRepository;
import me.luucka.mangashelf.catalog.dto.SeriesVolumeNumber;
import me.luucka.mangashelf.catalog.Series;
import me.luucka.mangashelf.catalog.Volume;
import me.luucka.mangashelf.collection.dto.EditionSummary;
import me.luucka.mangashelf.collection.dto.SeriesProgressResponse;
import me.luucka.mangashelf.collection.dto.UserVolumeResponse;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.AppUserRepository;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Operations on one user's shelf.
 *
 * <p>Every method scopes its queries by the principal's id, so no call here
 * can read or write another account's rows even though the catalogue
 * underneath is shared.
 */
@Service
public class CollectionService {

    private final UserVolumeRepository userVolumes;
    private final AppUserRepository users;
    private final CatalogService catalog;
    private final VolumeRepository volumes;

    public CollectionService(UserVolumeRepository userVolumes,
                             AppUserRepository users,
                             CatalogService catalog,
                             VolumeRepository volumes) {
        this.userVolumes = userVolumes;
        this.users = users;
        this.catalog = catalog;
        this.volumes = volumes;
    }

    @Transactional(readOnly = true)
    public List<UserVolumeResponse> listOwned(UserPrincipal principal) {
        return userVolumes.findByIdUserIdOrderByAddedAtDesc(principal.id())
                .stream().map(UserVolumeResponse::from).toList();
    }

    /**
     * Adds a copy to the shelf.
     *
     * <p>Returns the DTO rather than the entity: the response walks volume to
     * series to manga, and with {@code open-in-view: false} that path is
     * already closed once this method returns. Building it here, inside the
     * transaction, is the only point where the associations are still
     * reachable.
     */
    @Transactional
    public UserVolumeResponse add(Long volumeId, UserPrincipal principal) {
        if (userVolumes.existsByIdUserIdAndIdVolumeId(principal.id(), volumeId)) {
            throw ApiException.conflict("already_owned");
        }
        Volume volume = catalog.getVolume(volumeId);
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("user_not_found"));

        UserVolume saved = userVolumes.save(new UserVolume(user, volume));
        return UserVolumeResponse.from(saved);
    }

    @Transactional
    public void remove(Long volumeId, UserPrincipal principal) {
        UserVolume owned = userVolumes
                .findByIdUserIdAndIdVolumeId(principal.id(), volumeId)
                .orElseThrow(() -> ApiException.notFound("not_owned"));
        userVolumes.delete(owned);
    }

    /**
     * Marks a whole range as owned in one call.
     *
     * <p>Ticking off twenty volumes one request at a time is the friction
     * that stops a shelf from ever being recorded. Numbers already owned are
     * skipped rather than rejected, so the call is safe to repeat.
     *
     * @return the volume ids actually added
     */
    @Transactional
    public List<Long> addRange(Long seriesId, short from, short to,
                               UserPrincipal principal) {
        if (to < from) {
            throw ApiException.badRequest("invalid_range");
        }
        Series series = catalog.getSeries(seriesId);
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("user_not_found"));

        List<Long> added = new ArrayList<>();
        for (Volume volume : series.getVolumes()) {
            short n = volume.getNumber();
            if (n < from || n > to) continue;
            if (userVolumes.existsByIdUserIdAndIdVolumeId(principal.id(), volume.getId())) {
                continue;
            }
            userVolumes.save(new UserVolume(user, volume));
            added.add(volume.getId());
        }
        return added;
    }

    /**
     * The shelf grouped by edition, with what is owned and what is missing.
     *
     * <p>Two queries regardless of how many editions are involved: one for
     * the owned rows, one for every volume number of the editions they
     * touch. Asking each edition separately would issue a round trip per row
     * of the collection page.
     */
    @Transactional(readOnly = true)
    public List<EditionSummary> summary(UserPrincipal principal) {
        List<UserVolume> owned = userVolumes.findByIdUserIdOrderByAddedAtDesc(principal.id());
        if (owned.isEmpty()) {
            return List.of();
        }

        // LinkedHashMap: keeps the newest-first order the query returned, so
        // the caller can sort as it likes without the grouping shuffling it.
        Map<Long, List<UserVolume>> byEdition = new LinkedHashMap<>();
        for (UserVolume uv : owned) {
            byEdition.computeIfAbsent(uv.getVolume().getSeries().getId(),
                    id -> new ArrayList<>()).add(uv);
        }

        Map<Long, Set<Short>> catalogued = new LinkedHashMap<>();
        for (SeriesVolumeNumber row : volumes.findNumbersBySeriesIds(
                List.copyOf(byEdition.keySet()))) {
            catalogued.computeIfAbsent(row.seriesId(), id -> new TreeSet<>())
                    .add(row.number());
        }

        List<EditionSummary> result = new ArrayList<>();
        for (Map.Entry<Long, List<UserVolume>> entry : byEdition.entrySet()) {
            List<UserVolume> items = entry.getValue();
            Series series = items.getFirst().getVolume().getSeries();

            Set<Short> ownedNumbers = new TreeSet<>();
            for (UserVolume uv : items) {
                ownedNumbers.add(uv.getVolume().getNumber());
            }

            List<Short> missing = catalogued
                    .getOrDefault(entry.getKey(), Set.of()).stream()
                    .filter(n -> !ownedNumbers.contains(n))
                    .toList();

            result.add(new EditionSummary(
                    series.getId(),
                    series.getName(),
                    series.getPublisher(),
                    series.getManga().getId(),
                    series.getManga().displayTitle(),
                    series.getManga().getCoverUrl(),
                    ownedNumbers.size() + missing.size(),
                    ownedNumbers.size(),
                    List.copyOf(ownedNumbers),
                    missing));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SeriesProgressResponse progress(Long seriesId, UserPrincipal principal) {
        // Both queries below filter by series id and legitimately return
        // empty lists, so without this check a mistyped id would answer 200
        // with a plausible all-zero report for an edition that does not exist.
        Series series = catalog.getSeries(seriesId);

        List<Short> owned = userVolumes.findOwnedInSeries(principal.id(), seriesId)
                .stream().map(uv -> uv.getVolume().getNumber()).toList();
        List<Short> missing = userVolumes.findMissingNumbers(principal.id(), seriesId);

        return new SeriesProgressResponse(
                seriesId,
                series.getName(),
                series.getManga().displayTitle(),
                owned.size() + missing.size(),
                owned.size(),
                owned,
                missing);
    }
}
