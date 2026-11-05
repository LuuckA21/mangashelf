package me.luucka.mangashelf.purchases.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PurchaseSettings(
    @NotNull @DecimalMin("0.01") @DecimalMax("9.99") @Digits(integer = 1, fraction = 2)
        BigDecimal chfRate,
    @NotNull @Min(0) @Max(100) Integer discountPercent) {}
