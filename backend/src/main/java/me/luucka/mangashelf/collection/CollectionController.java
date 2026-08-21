package me.luucka.mangashelf.collection;

import me.luucka.mangashelf.collection.dto.EditionSummary;
import me.luucka.mangashelf.collection.dto.SeriesProgressResponse;
import me.luucka.mangashelf.collection.dto.UserVolumeResponse;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import me.luucka.mangashelf.common.ApiException;

import java.util.List;
import java.util.Map;

/**
 * Which volumes the signed-in user owns.
 *
 * <p>Open to any authenticated user, administrator or not: marking a volume
 * as owned records a fact about that person's shelf, and touches nothing
 * anybody else can see.
 */
@RestController
@RequestMapping("/api/collection")
public class CollectionController {

    /** Matches the limit the range call and purchase lines already enforce. */
    private static final short MAX_VOLUME_NUMBER = 999;

    private final CollectionService collection;

    public CollectionController(CollectionService collection) {
        this.collection = collection;
    }

    @GetMapping("/volumes")
    public List<UserVolumeResponse> listOwned(@AuthenticationPrincipal UserPrincipal principal) {
        return collection.listOwned(principal);
    }

    /** The shelf grouped by edition, with owned and missing numbers. */
    @GetMapping("/summary")
    public List<EditionSummary> summary(@AuthenticationPrincipal UserPrincipal principal) {
        return collection.summary(principal);
    }

    @PostMapping("/series/{seriesId}/volumes/{number}")
    public ResponseEntity<Void> add(@PathVariable Long seriesId,
                                    @PathVariable Short number,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        // Bounded here as well as in the range call: the shelf draws positive
        // numbers to the highest one owned, plus the optional volume 0. Marking
        // volume 30000 would make every later page build thirty thousand gaps.
        requireSaneNumber(number);
        collection.add(seriesId, number, principal);
        return ResponseEntity.noContent().build();
    }

    private void requireSaneNumber(Short number) {
        if (number == null || number < 0 || number > MAX_VOLUME_NUMBER) {
            throw ApiException.badRequest("invalid_volume_number");
        }
    }

    @DeleteMapping("/series/{seriesId}/volumes/{number}")
    public ResponseEntity<Void> remove(@PathVariable Long seriesId,
                                       @PathVariable Short number,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        requireSaneNumber(number);
        collection.remove(seriesId, number, principal);
        return ResponseEntity.noContent().build();
    }

    /** Marks volumes {@code from}..{@code to} of an edition as owned. */
    @PostMapping("/series/{seriesId}/range")
    public Map<String, Integer> addRange(@PathVariable Long seriesId,
                                         @RequestParam int from,
                                         @RequestParam int to,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("added", collection.addRange(seriesId, from, to, principal));
    }

    /** Owned numbers and gaps for one edition. */
    @GetMapping("/series/{seriesId}")
    public SeriesProgressResponse progress(@PathVariable Long seriesId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return collection.progress(seriesId, principal);
    }
}
