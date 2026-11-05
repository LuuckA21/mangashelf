package me.luucka.mangashelf.purchases;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, UUID> {

  Optional<PurchaseItem> findByIdAndListId(UUID id, UUID listId);
}
