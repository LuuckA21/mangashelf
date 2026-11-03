package me.luucka.mangashelf.collection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CollectionEditionRepository extends JpaRepository<CollectionEdition, UUID> {

  Optional<CollectionEdition> findByUserIdAndEditionId(UUID userId, UUID editionId);

  boolean existsByUserIdAndEditionId(UUID userId, UUID editionId);

  long countByEditionId(UUID editionId);

  /** Which of these editions the user has; one query for a whole series. */
  @Query(
      "select c.editionId from CollectionEdition c where c.userId = :userId and c.editionId in :editionIds")
  List<UUID> findEditionIds(UUID userId, Collection<UUID> editionIds);

  record EditionCount(UUID editionId, long count) {}

  /** Collection counts for many editions in one query; editions in no collection are absent. */
  @Query(
      "select new me.luucka.mangashelf.collection.CollectionEditionRepository$EditionCount("
          + "c.editionId, count(c)) from CollectionEdition c "
          + "where c.editionId in :editionIds group by c.editionId")
  List<EditionCount> countByEditionIds(Collection<UUID> editionIds);

  @Modifying
  @Query("delete from CollectionEdition c where c.userId = :userId and c.editionId = :editionId")
  void deleteByUserIdAndEditionId(UUID userId, UUID editionId);
}
