package me.luucka.mangashelf.purchase.dto;

/**
 * What happened when a purchase list was moved onto the shelf.
 *
 * @param added        volumes marked as owned by this call
 * @param alreadyOwned lines whose volume was already on the shelf
 */
public record TransferResult(int added, int alreadyOwned) {
}
