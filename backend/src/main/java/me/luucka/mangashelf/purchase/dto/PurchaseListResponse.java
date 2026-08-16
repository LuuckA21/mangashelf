package me.luucka.mangashelf.purchase.dto;

import me.luucka.mangashelf.purchase.PurchaseItem;
import me.luucka.mangashelf.purchase.PurchaseList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * A purchase list with its lines and its arithmetic already done.
 *
 * <p>Totals are computed here rather than in the browser so that the
 * rounding of a percentage discount happens once, in one place, and the
 * figure on screen is the figure the server would bill.
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
        int totalEurCents,
        int subtotalChfCents,
        int discountAppliedCents,
        int totalChfCents
) {

    public static PurchaseListResponse from(PurchaseList list) {
        int eur = 0;
        int chf = 0;
        int reserved = 0;
        for (PurchaseItem item : list.getItems()) {
            if (item.getPriceEurCents() != null) eur += item.getPriceEurCents();
            if (item.getPriceChfCents() != null) chf += item.getPriceChfCents();
            if (item.isReserved()) reserved++;
        }

        int discount = 0;
        if (list.getDiscountPercent() != null) {
            // HALF_UP on the whole subtotal, not per line: discounting each
            // row separately and summing gives a different figure, and the
            // shop discounts the order.
            discount = BigDecimal.valueOf(chf)
                    .multiply(list.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .intValue();
        } else if (list.getDiscountCents() != null) {
            discount = list.getDiscountCents();
        }
        // A flat discount larger than the list must not produce a negative
        // total: the shop would not pay you.
        discount = Math.min(discount, chf);

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
                eur,
                chf,
                discount,
                chf - discount);
    }
}
