package me.luucka.mangashelf.purchases;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, UUID> {

  Optional<PurchaseItem> findByIdAndListId(UUID id, UUID listId);

  List<PurchaseItem> findByListId(UUID listId);

  boolean existsByEditionId(UUID editionId);

  /** Highest volume of an edition in any user's rows. */
  @Query("select max(i.volume) from PurchaseItem i where i.editionId = :editionId")
  Optional<Integer> findMaxVolumeByEditionId(UUID editionId);

  /** Acquired = a booked row in one of the user's closed lists; reads the flushed state. */
  @Query(
      "select count(i) > 0 from PurchaseItem i, PurchaseList l where l.id = i.listId"
          + " and l.userId = :userId and l.closed = true and i.booked = true"
          + " and i.editionId = :editionId and i.volume = :volume")
  boolean isAcquired(UUID userId, UUID editionId, int volume);
}
