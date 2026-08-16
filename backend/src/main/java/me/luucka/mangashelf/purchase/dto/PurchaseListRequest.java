package me.luucka.mangashelf.purchase.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param discountPercent percentage off the CHF total, or null
 * @param discountCents   flat amount off the CHF total in cents, or null
 */
public record PurchaseListRequest(
        @NotBlank @Size(max = 200) String name,
        @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
        @Min(0) Integer discountCents
) {
}
