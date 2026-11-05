package me.luucka.mangashelf.collection;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OwnedVolumeRepository extends JpaRepository<OwnedVolume, UUID> {

  /** Highest owned number of an edition across all users. */
  @Query(
      "select max(v.number) from OwnedVolume v where v.collectionEditionId in "
          + "(select c.id from CollectionEdition c where c.editionId = :editionId)")
  Optional<Integer> findMaxNumberByEditionId(UUID editionId);

  boolean existsByCollectionEditionIdAndNumber(UUID collectionEditionId, int number);

  @Modifying(flushAutomatically = true)
  @Query(
      "delete from OwnedVolume v where v.collectionEditionId = :collectionEditionId"
          + " and v.number = :number")
  void deleteByCollectionEditionIdAndNumber(UUID collectionEditionId, int number);

  // Bulk delete runs immediately; derived deletes would be flushed after the re-inserts.
  @Modifying(flushAutomatically = true)
  @Query("delete from OwnedVolume v where v.collectionEditionId = :collectionEditionId")
  void deleteByCollectionEditionId(UUID collectionEditionId);
}
