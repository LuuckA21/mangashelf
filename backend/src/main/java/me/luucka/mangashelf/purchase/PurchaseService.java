package me.luucka.mangashelf.purchase;

import me.luucka.mangashelf.catalog.CatalogService;
import me.luucka.mangashelf.collection.UserVolume;
import me.luucka.mangashelf.collection.UserVolumeRepository;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.purchase.dto.PurchaseItemRequest;
import me.luucka.mangashelf.purchase.dto.PurchaseListRequest;
import me.luucka.mangashelf.purchase.dto.PurchaseListResponse;
import me.luucka.mangashelf.purchase.dto.PurchaseListSummary;
import me.luucka.mangashelf.purchase.dto.PurchaseStats;
import me.luucka.mangashelf.purchase.dto.PurchaseSuggestion;
import me.luucka.mangashelf.purchase.dto.TransferResult;
import me.luucka.mangashelf.purchase.dto.YearStats;
import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.AppUserRepository;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Purchase lists, scoped to their owner.
 *
 * <p>Every lookup goes through the user id rather than fetching by primary
 * key and checking afterwards: a list that is not yours simply is not found,
 * so there is no path where a missing check exposes one.
 */
@Service
public class PurchaseService {

    /** Matches the ceiling {@code PurchaseItemRequest} enforces. */
    private static final int MAX_VOLUME_NUMBER = 999;

    private final PurchaseListRepository lists;
    private final PurchaseItemRepository items;
    private final AppUserRepository users;
    private final CatalogService catalog;
    private final UserVolumeRepository userVolumes;

    public PurchaseService(PurchaseListRepository lists,
                           PurchaseItemRepository items,
                           AppUserRepository users,
                           CatalogService catalog,
                           UserVolumeRepository userVolumes) {
        this.lists = lists;
        this.items = items;
        this.users = users;
        this.catalog = catalog;
        this.userVolumes = userVolumes;
    }

    @Transactional(readOnly = true)
    public List<PurchaseListSummary> listAll(UserPrincipal principal) {
        return lists.findByUserIdOrderByCreatedAtDesc(principal.id()).stream()
                .map(list -> {
                    // Counted and summed straight off the entities. Building
                    // the full response here would map every line into a DTO
                    // that names its edition and work, and those are lazy:
                    // a page of twenty lists would issue hundreds of queries
                    // to produce three numbers.
                    int chf = 0;
                    int reserved = 0;
                    int purchased = 0;
                    for (PurchaseItem item : list.getItems()) {
                        if (item.getPriceChfCents() != null) chf += item.getPriceChfCents();
                        if (item.isReserved()) reserved++;
                        if (item.getPurchasedAt() != null) purchased++;
                    }
                    return new PurchaseListSummary(
                            list.getId(), list.getName(),
                            list.getPeriodYear(), list.getPeriodMonth(),
                            list.getPaidAt(),
                            list.getItems().size(), reserved, purchased,
                            chf - list.discountOn(chf));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseListResponse get(Long id, UserPrincipal principal) {
        return PurchaseListResponse.from(load(id, principal));
    }

    @Transactional
    public PurchaseListResponse create(PurchaseListRequest request, UserPrincipal principal) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("user_not_found"));

        PurchaseList list = new PurchaseList(user, request.name());
        applyPeriod(list, request);
        applyDiscount(list, request);
        return PurchaseListResponse.from(lists.save(list));
    }

    @Transactional
    public PurchaseListResponse update(Long id, PurchaseListRequest request,
                                       UserPrincipal principal) {
        PurchaseList list = load(id, principal);
        requireOpen(list);
        list.setName(request.name());
        applyPeriod(list, request);
        applyDiscount(list, request);
        return PurchaseListResponse.from(list);
    }

    @Transactional
    public void delete(Long id, UserPrincipal principal) {
        lists.delete(load(id, principal));
    }

    @Transactional
    public PurchaseListResponse addItem(Long listId, PurchaseItemRequest request,
                                        UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        requireOpen(list);

        PurchaseItem item = new PurchaseItem(
                catalog.getSeries(request.seriesId()), request.volumeNumber());
        item.setReleaseDate(request.releaseDate());
        item.setPriceEurCents(request.priceEurCents());
        item.setPriceChfCents(request.priceChfCents());

        list.addItem(item);

        // Flushed before the response is built: the insert would otherwise
        // wait for the end of the transaction, and the new line would go
        // back with a null id — which the client cannot then edit or delete.
        lists.flush();

        return PurchaseListResponse.from(list);
    }

    /**
     * Edits one line.
     *
     * <p>The edition can move too: a volume filed under the wrong run is a
     * plausible mistake, and forcing a delete-and-retype to fix it would
     * lose the date and prices already entered.
     */
    @Transactional
    public PurchaseListResponse updateItem(Long listId, Long itemId,
                                           PurchaseItemRequest request,
                                           UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        requireOpen(list);
        PurchaseItem item = list.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("item_not_found"));

        item.setSeries(catalog.getSeries(request.seriesId()));
        item.setVolumeNumber(request.volumeNumber());
        item.setReleaseDate(request.releaseDate());
        item.setPriceEurCents(request.priceEurCents());
        item.setPriceChfCents(request.priceChfCents());
        return PurchaseListResponse.from(list);
    }

    @Transactional
    public PurchaseListResponse removeItem(Long listId, Long itemId, UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        requireOpen(list);
        boolean removed = list.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw ApiException.notFound("item_not_found");
        }
        return PurchaseListResponse.from(list);
    }

    /**
     * What the lists cost, grouped by year.
     *
     * <p>The year comes from the period a list declares, falling back to the
     * year it was created: a list with no period would otherwise vanish from
     * the figures, and a total that quietly omits rows is worse than none.
     *
     * <p>Only lines carrying a franc price count. Including priced and
     * unpriced lines in the same denominator would make the averages read
     * lower the more incomplete the data is.
     *
     * <p>And only lines marked as bought. Closing a list says the month is
     * over, not that everything on it was taken: what was not bought is
     * carried into the next one, and until it is bought it was not spent.
     */
    @Transactional(readOnly = true)
    public PurchaseStats stats(UserPrincipal principal) {
        Map<Integer, int[]> byYear = new HashMap<>();   // [liste, volumi, pieno, sconto]

        for (PurchaseList list : lists.findByUserIdOrderByCreatedAtDesc(principal.id())) {
            int year = list.getPeriodYear() != null
                    ? list.getPeriodYear()
                    : list.getCreatedAt().atZone(ZoneId.systemDefault()).getYear();

            int full = 0;
            int priced = 0;
            for (PurchaseItem item : list.getItems()) {
                if (item.getPriceChfCents() != null && item.getPurchasedAt() != null) {
                    full += item.getPriceChfCents();
                    priced++;
                }
            }

            int discount = list.discountOn(full);

            int[] row = byYear.computeIfAbsent(year, y -> new int[4]);
            row[0] += 1;
            row[1] += priced;
            row[2] += full;
            row[3] += discount;
        }

        List<YearStats> years = byYear.entrySet().stream()
                .sorted(Map.Entry.<Integer, int[]>comparingByKey().reversed())
                .map(entry -> {
                    int[] row = entry.getValue();
                    int net = row[2] - row[3];
                    return new YearStats(entry.getKey(), row[0], row[1],
                            row[2], row[3], net,
                            average(row[2], row[1]), average(net, row[1]));
                })
                .toList();

        int listCount = years.stream().mapToInt(YearStats::listCount).sum();
        int volumes = years.stream().mapToInt(YearStats::volumeCount).sum();
        int full = years.stream().mapToInt(YearStats::fullChfCents).sum();
        int discount = years.stream().mapToInt(YearStats::discountChfCents).sum();
        int net = full - discount;

        return new PurchaseStats(years, listCount, volumes, full, discount, net,
                average(full, volumes), average(net, volumes));
    }

    /** Zero rather than a division by zero when a year has no priced line. */
    private int average(int totalCents, int count) {
        return count == 0 ? 0 : Math.round((float) totalCents / count);
    }

    /**
     * What to add next, read from what was bought before.
     *
     * <p>For every run this user has ever put on a list, proposes the volume
     * after the highest one bought, carrying the prices of that purchase. A
     * new volume of an ongoing series is last month's line with the number
     * moved on by one, and retyping it is the friction this removes.
     *
     * <p>Runs already covered in the target list are left out: suggesting a
     * line that is on screen just below would be noise.
     */
    @Transactional(readOnly = true)
    public List<PurchaseSuggestion> suggestions(Long listId, UserPrincipal principal) {
        PurchaseList target = load(listId, principal);

        // What the list already asks for, so the same volume is not offered
        // twice — by series and by number, since two runs of one work can
        // both be in progress.
        Set<String> present = target.getItems().stream()
                .map(item -> item.getSeries().getId() + "#" + item.getVolumeNumber())
                .collect(Collectors.toSet());

        // Ordered by number ascending, so the last write per series wins and
        // holds both the highest number and the prices that went with it.
        Map<Long, PurchaseItem> latestPerSeries = new LinkedHashMap<>();
        for (PurchaseItem item : items.findPurchasedByUser(principal.id())) {
            latestPerSeries.put(item.getSeries().getId(), item);
        }

        List<PurchaseSuggestion> result = new ArrayList<>();
        for (PurchaseItem last : latestPerSeries.values()) {
            int next = last.getVolumeNumber() + 1;
            // Past the number a line may carry, so proposing it would only
            // produce a suggestion the validator refuses.
            if (next > MAX_VOLUME_NUMBER) {
                continue;
            }
            if (present.contains(last.getSeries().getId() + "#" + next)) {
                continue;
            }
            result.add(new PurchaseSuggestion(
                    last.getSeries().getId(),
                    last.getSeries().getName(),
                    last.getSeries().getPublisher(),
                    last.getSeries().getManga().displayTitle(),
                    (short) next,
                    last.getPriceEurCents(),
                    last.getPriceChfCents(),
                    last.getList().getName()));
        }

        result.sort(Comparator.comparing(PurchaseSuggestion::mangaTitle,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Marks every volume of the list as owned.
     *
     * <p>Deliberately not tied to marking the list paid: paying and owning
     * are different facts, and an action that silently triggers another
     * makes it hard to tell what happened.
     *
     * <p>Nothing is created in the shared catalogue — ownership is a row of
     * its own — so this works the same for every user, administrator or not.
     *
     * <p>Repeatable: lines already on the shelf are counted, not duplicated.
     */
    @Transactional
    public TransferResult toCollection(Long listId, UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("user_not_found"));

        int added = 0;
        int alreadyOwned = 0;

        for (PurchaseItem item : list.getItems()) {
            Long seriesId = item.getSeries().getId();
            Short number = item.getVolumeNumber();

            if (userVolumes.existsByIdUserIdAndIdSeriesIdAndIdNumber(
                    principal.id(), seriesId, number)) {
                alreadyOwned++;
                continue;
            }
            userVolumes.save(new UserVolume(user, item.getSeries(), number));
            added++;
        }

        return new TransferResult(added, alreadyOwned);
    }

    /** Marks the list paid, or reopens it. */
    @Transactional
    public PurchaseListResponse setPaid(Long id, boolean paid, UserPrincipal principal) {
        PurchaseList list = load(id, principal);
        // Re-marking an already paid list keeps the original date: the
        // interesting fact is when it was settled, not when the button was
        // last pressed.
        if (paid && list.getPaidAt() == null) {
            list.setPaidAt(Instant.now());
        } else if (!paid) {
            list.setPaidAt(null);
        }
        return PurchaseListResponse.from(list);
    }

    /** Marks one line as bought, or takes the mark back. */
    @Transactional
    public PurchaseListResponse setPurchased(Long listId, Long itemId, boolean purchased,
                                             UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        requireOpen(list);
        PurchaseItem item = itemOf(list, itemId);
        // Re-marking keeps the original moment: what is interesting is when
        // the volume was bought, not when the button was last pressed.
        if (purchased && item.getPurchasedAt() == null) {
            item.setPurchasedAt(Instant.now());
        } else if (!purchased) {
            item.setPurchasedAt(null);
        }
        return PurchaseListResponse.from(list);
    }

    /**
     * Moves the lines that were not bought from one list into another.
     *
     * <p>They are moved rather than copied: what stays behind is then what
     * the month actually cost, which is the only reading of an old list that
     * is worth keeping.
     *
     * <p>Works on a closed list too, which is where the leftovers usually
     * are: the month gets settled, then the next list is written. Moving a
     * line that was never bought changes nothing about what that month cost,
     * so the lock on a paid list does not apply here.
     *
     * @return how many lines moved
     */
    @Transactional
    public int carryOver(Long targetId, Long sourceId, UserPrincipal principal) {
        if (targetId.equals(sourceId)) {
            throw ApiException.badRequest("same_list");
        }
        PurchaseList target = load(targetId, principal);
        requireOpen(target);
        PurchaseList source = load(sourceId, principal);

        List<PurchaseItem> pending = source.getItems().stream()
                .filter(item -> item.getPurchasedAt() == null)
                .toList();

        for (PurchaseItem item : pending) {
            source.getItems().remove(item);

            // A fresh line rather than a reparented one: orphanRemoval would
            // otherwise delete the row it has just seen leave the collection,
            // and the reservation does not travel — the shop was holding it
            // for a month that is over.
            PurchaseItem moved = new PurchaseItem(item.getSeries(), item.getVolumeNumber());
            moved.setReleaseDate(item.getReleaseDate());
            moved.setPriceEurCents(item.getPriceEurCents());
            moved.setPriceChfCents(item.getPriceChfCents());
            target.addItem(moved);
        }

        lists.flush();
        return pending.size();
    }

    /**
     * Refuses to touch a list that has been settled.
     *
     * <p>Closing a month is a statement about what happened, and a figure
     * that keeps moving afterwards is not a record of anything. Reopening
     * the list is the way back — an explicit act, unlike an edit that would
     * quietly change a settled total.
     *
     * <p>Carrying unbought lines out is the one exception: those were never
     * part of what the month cost.
     */
    private void requireOpen(PurchaseList list) {
        if (list.isPaid()) {
            throw ApiException.conflict("list_is_paid");
        }
    }

    private PurchaseItem itemOf(PurchaseList list, Long itemId) {
        return list.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("item_not_found"));
    }

    /** Marks one line as set aside at the shop, or clears it. */
    @Transactional
    public PurchaseListResponse setReserved(Long listId, Long itemId, boolean reserved,
                                            UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        requireOpen(list);
        itemOf(list, itemId).setReserved(reserved);
        return PurchaseListResponse.from(list);
    }

    /**
     * Year and month travel together: the schema rejects one without the
     * other, because a month with no year identifies nothing.
     */
    private void applyPeriod(PurchaseList list, PurchaseListRequest request) {
        boolean complete = request.periodYear() != null && request.periodMonth() != null;
        list.setPeriodYear(complete ? request.periodYear() : null);
        list.setPeriodMonth(complete ? request.periodMonth() : null);
    }

    /**
     * Only one discount form survives a save.
     *
     * <p>The schema forbids both being set, so choosing here — percentage
     * wins when the client sends both — keeps a malformed request from
     * arriving at the database as a constraint violation.
     */
    private void applyDiscount(PurchaseList list, PurchaseListRequest request) {
        if (request.discountPercent() != null) {
            list.setDiscountPercent(request.discountPercent());
            list.setDiscountCents(null);
        } else {
            list.setDiscountPercent(null);
            list.setDiscountCents(request.discountCents());
        }
    }

    private PurchaseList load(Long id, UserPrincipal principal) {
        return lists.findWithItemsByIdAndUserId(id, principal.id())
                .orElseThrow(() -> ApiException.notFound("list_not_found"));
    }
}
