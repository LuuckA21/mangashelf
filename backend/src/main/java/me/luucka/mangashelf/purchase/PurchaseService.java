package me.luucka.mangashelf.purchase;

import me.luucka.mangashelf.catalog.CatalogService;
import me.luucka.mangashelf.common.ApiException;
import me.luucka.mangashelf.purchase.dto.PurchaseItemRequest;
import me.luucka.mangashelf.purchase.dto.PurchaseListRequest;
import me.luucka.mangashelf.purchase.dto.PurchaseListResponse;
import me.luucka.mangashelf.purchase.dto.PurchaseListSummary;
import me.luucka.mangashelf.user.AppUser;
import me.luucka.mangashelf.user.AppUserRepository;
import me.luucka.mangashelf.user.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Purchase lists, scoped to their owner.
 *
 * <p>Every lookup goes through the user id rather than fetching by primary
 * key and checking afterwards: a list that is not yours simply is not found,
 * so there is no path where a missing check exposes one.
 */
@Service
public class PurchaseService {

    private final PurchaseListRepository lists;
    private final AppUserRepository users;
    private final CatalogService catalog;

    public PurchaseService(PurchaseListRepository lists,
                           AppUserRepository users,
                           CatalogService catalog) {
        this.lists = lists;
        this.users = users;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<PurchaseListSummary> listAll(UserPrincipal principal) {
        return lists.findByUserIdOrderByCreatedAtDesc(principal.id()).stream()
                .map(list -> {
                    PurchaseListResponse full = PurchaseListResponse.from(list);
                    return new PurchaseListSummary(
                            list.getId(), list.getName(),
                            list.getPeriodYear(), list.getPeriodMonth(),
                            list.getPaidAt(),
                            full.items().size(), full.reservedCount(),
                            full.totalChfCents());
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

        PurchaseItem item = new PurchaseItem(
                catalog.getSeries(request.seriesId()), request.volumeNumber());
        item.setReleaseDate(request.releaseDate());
        item.setPriceEurCents(request.priceEurCents());
        item.setPriceChfCents(request.priceChfCents());

        list.addItem(item);
        return PurchaseListResponse.from(list);
    }

    @Transactional
    public PurchaseListResponse removeItem(Long listId, Long itemId, UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        boolean removed = list.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw ApiException.notFound("item_not_found");
        }
        return PurchaseListResponse.from(list);
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

    /** Marks one line as set aside at the shop, or clears it. */
    @Transactional
    public PurchaseListResponse setReserved(Long listId, Long itemId, boolean reserved,
                                            UserPrincipal principal) {
        PurchaseList list = load(listId, principal);
        PurchaseItem item = list.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("item_not_found"));
        item.setReserved(reserved);
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
