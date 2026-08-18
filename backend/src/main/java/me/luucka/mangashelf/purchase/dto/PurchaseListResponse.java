package me.luucka.mangashelf.purchase.dto;

import me.luucka.mangashelf.purchase.PurchaseItem;
import me.luucka.mangashelf.purchase.PurchaseList;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A purchase list with its lines and its arithmetic already done.
 *
 * <p>Totals are computed here rather than in the browser so that the
 * rounding of a percentage discount happens once, in one place, and the
 * figure on screen is the figure the server would bill.
 *
 * <p>The totals cover the whole list, bought or not: what matters while
 * shopping is what the trip will cost. What was actually spent is a
 * different question, and the statistics answer it.
 *
 * @param subtotalChfCents before the discount
 * @param discountAppliedCents what the discount actually took off
 * @param totalChfCents  what is left to pay
 */
public record PurchaseListResponse(
        Long id,
        String name,
        Short periodYear,
        Short periodMonth,
        Instant paidAt,
        BigDecimal discountPercent,
        Integer discountCents,
        List<PurchaseItemResponse> items,
        int reservedCount,
        int purchasedCount,
        int totalEurCents,
        int subtotalChfCents,
        int discountAppliedCents,
        int totalChfCents
) {

    public static PurchaseListResponse from(PurchaseList list) {
        int eur = 0;
        int chf = 0;
        int reserved = 0;
        int purchased = 0;
        for (PurchaseItem item : list.getItems()) {
            if (item.getPriceEurCents() != null) eur += item.getPriceEurCents();
            if (item.getPriceChfCents() != null) chf += item.getPriceChfCents();
            if (item.isReserved()) reserved++;
            if (item.getPurchasedAt() != null) purchased++;
        }

        int discount = list.discountOn(chf);

        return new PurchaseListResponse(
                list.getId(),
                list.getName(),
                list.getPeriodYear(),
                list.getPeriodMonth(),
                list.getPaidAt(),
                list.getDiscountPercent(),
                list.getDiscountCents(),
                list.getItems().stream().map(PurchaseItemResponse::from).toList(),
                reserved,
                purchased,
                eur,
                chf,
                discount,
                chf - discount);
    }
}
