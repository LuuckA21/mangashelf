package me.luucka.mangashelf.collection;

import me.luucka.mangashelf.catalog.CatalogService;
import me.luucka.mangashelf.catalog.Series;
import me.luucka.mangashelf.catalog.Volume;
import me.luucka.mangashelf.collection.dto.SeriesProgressResponse;
import me.luucka.mangashelf.collection.dto.UserVolumeResponse;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.AppUserRepository;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Aggiunge una copia allo scaffale.
     *
     * <p>Restituisce il DTO e non l'entita': la risposta risale da volume a
     * serie a opera, e con {@code open-in-view: false} quel percorso non e'
     * piu' percorribile una volta usciti dal metodo. Costruirlo qui, dentro
     * la transazione, e' l'unico punto in cui le associazioni sono ancora
     * raggiungibili.
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
