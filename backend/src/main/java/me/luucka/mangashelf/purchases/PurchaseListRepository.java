package me.luucka.mangashelf.purchases;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseListRepository extends JpaRepository<PurchaseList, UUID> {

  Optional<PurchaseList> findByIdAndUserId(UUID id, UUID userId);
}
