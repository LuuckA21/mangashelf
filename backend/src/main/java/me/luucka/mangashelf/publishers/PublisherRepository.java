package me.luucka.mangashelf.publishers;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PublisherRepository extends JpaRepository<Publisher, UUID> {

  @Query("select p from Publisher p order by lower(p.name)")
  List<Publisher> findAllSorted();

  /** Another publisher whose name matches ignoring case; {@code name} must be stripped. */
  @Query("select count(p) > 0 from Publisher p where lower(p.name) = lower(:name) and p.id <> :id")
  boolean existsOtherWithName(String name, UUID id);

  @Query("select count(p) > 0 from Publisher p where lower(p.name) = lower(:name)")
  boolean existsWithName(String name);

  @Query("select count(e) > 0 from Edition e where e.publisherId = :id")
  boolean isUsed(UUID id);
}
