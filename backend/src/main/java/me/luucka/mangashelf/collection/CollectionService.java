package me.luucka.mangashelf.collection;

import me.luucka.mangashelf.catalog.CatalogService;
import me.luucka.mangashelf.catalog.Series;
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
import java.util.TreeSet;

/**
 * One user's shelf: which volume numbers they own of each edition.
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

    public CollectionService(UserVolumeRepository userVolumes,
                             AppUserRepository users,
                             CatalogService catalog) {
        this.userVolumes = userVolumes;
        this.users = users;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<UserVolumeResponse> listOwned(UserPrincipal principal) {
        return userVolumes.findByIdUserIdOrderByAddedAtDesc(principal.id())
                .stream().map(UserVolumeResponse::from).toList();
    }

    @Transactional
    public void add(Long seriesId, Short number, UserPrincipal principal) {
        if (userVolumes.existsByIdUserIdAndIdSeriesIdAndIdNumber(
                principal.id(), seriesId, number)) {
            throw ApiException.conflict("already_owned");
        }
        Series series = catalog.getSeries(seriesId);
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("user_not_found"));
        userVolumes.save(new UserVolume(user, series, number));
    }

    @Transactional
    public void remove(Long seriesId, Short number, UserPrincipal principal) {
        if (!userVolumes.existsByIdUserIdAndIdSeriesIdAndIdNumber(
                principal.id(), seriesId, number)) {
            throw ApiException.notFound("not_owned");
        }
        userVolumes.deleteByIdUserIdAndIdSeriesIdAndIdNumber(
                principal.id(), seriesId, number);
    }

    /**
     * Marks a whole range as owned.
     *
     * <p>Ticking off twenty volumes one request at a time is the friction
     * that stops a shelf from ever being recorded. Numbers already owned are
     * skipped rather than rejected, so the call is safe to repeat.
     *
     * @return how many were added
     */
    @Transactional
    public int addRange(Long seriesId, short from, short to, UserPrincipal principal) {
        if (to < from) {
            throw ApiException.badRequest("invalid_range");
        }
        Series series = catalog.getSeries(seriesId);
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("user_not_found"));

        int added = 0;
        for (short n = from; n <= to; n++) {
            if (userVolumes.existsByIdUserIdAndIdSeriesIdAndIdNumber(
                    principal.id(), seriesId, n)) {
                continue;
            }
            userVolumes.save(new UserVolume(user, series, n));
            added++;
        }
        return added;
    }

    @Transactional(readOnly = true)
    public SeriesProgressResponse progress(Long seriesId, UserPrincipal principal) {
        // Checked rather than assumed: the two queries below filter by id and
        // would answer with an empty, plausible-looking shelf for an edition
        // that does not exist.
        Series series = catalog.getSeries(seriesId);

        return SeriesProgressResponse.of(
                seriesId, series.getName(), series.getManga().displayTitle(),
                series.getTotalVolumes(),
                userVolumes.findNumbers(principal.id(), seriesId));
    }

    /**
     * The shelf grouped by edition, with what is owned and what is missing.
     *
     * <p>One query: the owned rows carry their edition and work along, and
     * the gaps are worked out from the numbers themselves.
     */
    @Transactional(readOnly = true)
    public List<EditionSummary> summary(UserPrincipal principal) {
        Map<Long, TreeSet<Short>> numbersByEdition = new LinkedHashMap<>();
        Map<Long, Series> editions = new LinkedHashMap<>();

        for (UserVolume uv : userVolumes.findByIdUserIdOrderByAddedAtDesc(principal.id())) {
            Long seriesId = uv.getSeries().getId();
            editions.putIfAbsent(seriesId, uv.getSeries());
            numbersByEdition.computeIfAbsent(seriesId, id -> new TreeSet<>())
                    .add(uv.getNumber());
        }

        List<EditionSummary> result = new ArrayList<>();
        for (Map.Entry<Long, Series> entry : editions.entrySet()) {
            Series series = entry.getValue();
            List<Short> owned = List.copyOf(numbersByEdition.get(entry.getKey()));

            SeriesProgressResponse progress = SeriesProgressResponse.of(
                    series.getId(), series.getName(),
                    series.getManga().displayTitle(),
                    series.getTotalVolumes(), owned);

            result.add(new EditionSummary(
                    series.getId(),
                    series.getName(),
                    series.getPublisher(),
                    series.getManga().getId(),
                    series.getManga().displayTitle(),
                    series.getManga().getCoverUrl(),
                    series.getTotalVolumes(),
                    progress.upTo(),
                    owned.size(),
                    owned,
                    progress.missingNumbers()));
        }
        return result;
    }
}
