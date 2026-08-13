package me.luucka.mangashelf.collection;

import me.luucka.mangashelf.collection.dto.SeriesProgressResponse;
import me.luucka.mangashelf.collection.dto.UserVolumeResponse;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Which volumes the signed-in user owns. */
@RestController
@RequestMapping("/api/collection")
public class CollectionController {

    private final CollectionService collection;

    public CollectionController(CollectionService collection) {
        this.collection = collection;
    }

    @GetMapping("/volumes")
    public List<UserVolumeResponse> listOwned(@AuthenticationPrincipal UserPrincipal principal) {
        return collection.listOwned(principal).stream()
                .map(UserVolumeResponse::from).toList();
    }

    @PostMapping("/volumes/{volumeId}")
    public ResponseEntity<UserVolumeResponse> add(
            @PathVariable Long volumeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserVolume owned = collection.add(volumeId, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserVolumeResponse.from(owned));
    }

    @DeleteMapping("/volumes/{volumeId}")
    public ResponseEntity<Void> remove(@PathVariable Long volumeId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        collection.remove(volumeId, principal);
        return ResponseEntity.noContent().build();
    }

    /** Marks volumes {@code from}..{@code to} of an edition as owned. */
    @PostMapping("/series/{seriesId}/range")
    public Map<String, Object> addRange(@PathVariable Long seriesId,
                                        @RequestParam short from,
                                        @RequestParam short to,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        List<Long> added = collection.addRange(seriesId, from, to, principal);
        return Map.of("added", added.size(), "volumeIds", added);
    }

    /** Owned numbers and gaps for one edition. */
    @GetMapping("/series/{seriesId}")
    public SeriesProgressResponse progress(@PathVariable Long seriesId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return collection.progress(seriesId, principal);
    }
}
