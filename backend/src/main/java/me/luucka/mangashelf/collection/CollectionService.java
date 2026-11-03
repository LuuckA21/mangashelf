package me.luucka.mangashelf.collection;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.luucka.mangashelf.catalog.EditionService;
import me.luucka.mangashelf.catalog.EditionStatus;
import me.luucka.mangashelf.catalog.EditionType;
import me.luucka.mangashelf.collection.dto.CollectionEntry;
import me.luucka.mangashelf.common.FieldValidationException;
import me.luucka.mangashelf.common.LikeEscape;
import me.luucka.mangashelf.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionService {

  private static final String FROM =
      """
      from collection_editions ce
      join editions e on e.id = ce.edition_id
      join series s on s.id = e.series_id
      where ce.user_id = :userId
      """;

  private static final String OWNED_COUNT =
      "(select count(*) from owned_volumes ov where ov.collection_edition_id = ce.id)";

  private final CollectionEditionRepository collection;
  private final OwnedVolumeRepository volumes;
  private final EditionService editions;
  private final JdbcClient jdbc;

  public CollectionService(
      CollectionEditionRepository collection,
      OwnedVolumeRepository volumes,
      EditionService editions,
      JdbcClient jdbc) {
    this.collection = collection;
    this.volumes = volumes;
    this.editions = editions;
    this.jdbc = jdbc;
  }

  /** Sort is fixed; any sort on the Pageable is ignored. */
  @Transactional(readOnly = true)
  public Page<CollectionEntry> list(
      UUID userId,
      String q,
      EditionType type,
      EditionStatus status,
      boolean incomplete,
      Pageable pageable) {
    var where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();
    params.put("userId", userId);
    var text = q == null ? "" : q.strip();
    if (!text.isEmpty()) {
      where.append(
          " and (lower(s.title) like :q escape '\\' or lower(e.name) like :q escape '\\')");
      params.put("q", "%" + LikeEscape.escape(text.toLowerCase()) + "%");
    }
    if (type != null) {
      where.append(" and e.type = :type");
      params.put("type", type.name());
    }
    if (status != null) {
      where.append(" and e.status = :status");
      params.put("status", status.name());
    }
    if (incomplete) {
      where.append(" and ").append(OWNED_COUNT).append(" < e.volume_count");
    }
    var total =
        jdbc.sql("select count(*) " + FROM + where).params(params).query(Long.class).single();
    var content = query(where, params, " limit :limit offset :offset", pageable);
    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntry> listAll(UUID userId) {
    return query(new StringBuilder(), Map.of("userId", userId), "", null);
  }

  /** Idempotent: adding an edition already in the collection changes nothing. */
  @Transactional
  public CollectionEntry add(UUID userId, UUID editionId) {
    editions.require(editionId);
    ensure(userId, editionId);
    return entry(userId, editionId);
  }

  @Transactional
  public void remove(UUID userId, UUID editionId) {
    editions.require(editionId);
    collection.deleteByUserIdAndEditionId(userId, editionId); // volumes go by cascade
  }

  /** Replaces the whole owned set; last write wins. */
  @Transactional
  public CollectionEntry setOwned(UUID userId, UUID editionId, List<Integer> owned) {
    var edition = editions.require(editionId);
    var distinct = new HashSet<>(owned);
    if (distinct.size() != owned.size()) {
      throw new FieldValidationException("owned", "Numeri duplicati");
    }
    if (owned.stream().anyMatch(n -> n < 1 || n > edition.getVolumeCount())) {
      throw new FieldValidationException(
          "owned", "I numeri devono essere tra 1 e " + edition.getVolumeCount());
    }
    var ce = ensure(userId, editionId);
    volumes.deleteByCollectionEditionId(ce.getId());
    var now = Instant.now();
    volumes.saveAllAndFlush(owned.stream().map(n -> new OwnedVolume(ce.getId(), n, now)).toList());
    return entry(userId, editionId);
  }

  private CollectionEdition ensure(UUID userId, UUID editionId) {
    return collection
        .findByUserIdAndEditionId(userId, editionId)
        .orElseGet(
            () -> collection.saveAndFlush(new CollectionEdition(userId, editionId, Instant.now())));
  }

  private CollectionEntry entry(UUID userId, UUID editionId) {
    return query(
            new StringBuilder(" and e.id = :editionId"),
            Map.of("userId", userId, "editionId", editionId),
            "",
            null)
        .stream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Edizione non trovata nella collezione"));
  }

  private List<CollectionEntry> query(
      StringBuilder where, Map<String, Object> params, String paging, Pageable pageable) {
    var sql =
        """
        select e.id edition_id, s.id series_id, s.title, e.name, e.publisher, e.type, e.status,
               e.volume_count, e.cover_file is not null has_cover,
               array(select ov.number from owned_volumes ov
                     where ov.collection_edition_id = ce.id order by ov.number) owned
        """
            + FROM
            + where
            + " order by lower(s.title), lower(e.name), e.id"
            + paging;
    var stmt = jdbc.sql(sql).params(params);
    if (pageable != null) {
      stmt = stmt.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
    }
    return stmt.query(
            (rs, i) -> {
              var owned = new ArrayList<Integer>();
              try {
                for (var n : (Object[]) rs.getArray("owned").getArray()) {
                  owned.add((Integer) n);
                }
              } catch (SQLException ex) {
                throw new IllegalStateException(ex);
              }
              return new CollectionEntry(
                  rs.getString("edition_id"),
                  rs.getString("series_id"),
                  rs.getString("title"),
                  rs.getString("name"),
                  rs.getString("publisher"),
                  EditionType.valueOf(rs.getString("type")),
                  EditionStatus.valueOf(rs.getString("status")),
                  rs.getInt("volume_count"),
                  rs.getBoolean("has_cover"),
                  List.copyOf(owned));
            })
        .list();
  }
}
