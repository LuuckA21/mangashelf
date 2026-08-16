package me.luucka.mangashelf.purchase.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param periodYear      year of the month covered, or null
 * @param periodMonth     1-12, or null
 * @param discountPercent percentage off the CHF total, or null
 * @param discountCents   flat amount off the CHF total in cents, or null
 */
public record PurchaseListRequest(
        @NotBlank @Size(max = 200) String name,
        @Min(1900) @Max(2200) Short periodYear,
        @Min(1) @Max(12) Short periodMonth,
        @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
        @Min(0) Integer discountCents
) {
}
