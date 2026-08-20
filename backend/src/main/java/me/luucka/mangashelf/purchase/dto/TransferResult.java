package me.luucka.mangashelf.purchase.dto;

/**
 * What happened when a purchase list was moved onto the shelf.
 *
 * @param added         purchased volumes marked as owned by this call
 * @param alreadyOwned  purchased lines whose volume was already on the shelf
 * @param notPurchased  planned lines deliberately left out
 */
public record TransferResult(int added, int alreadyOwned, int notPurchased) {
}
