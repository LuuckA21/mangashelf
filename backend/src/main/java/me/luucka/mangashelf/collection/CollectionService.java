package me.luucka.mangashelf.collection;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.luucka.mangashelf.catalog.EditionService;
import me.luucka.mangashelf.catalog.EditionStatus;
import me.luucka.mangashelf.catalog.EditionType;
import me.luucka.mangashelf.collection.dto.CollectionEntry;
import me.luucka.mangashelf.collection.dto.CollectionStats;
import me.luucka.mangashelf.collection.dto.StatBar;
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

  @Transactional(readOnly = true)
  public CollectionStats stats(UUID userId) {
    var totals =
        jdbc.sql(
                """
                select count(*) editions, count(distinct e.series_id) series,
                       coalesce(sum(e.volume_count), 0) total, coalesce(sum(o.n), 0) owned,
                       count(*) filter (where o.n = e.volume_count and e.status = 'COMPLETED') done
                from collection_editions ce
                join editions e on e.id = ce.edition_id
                cross join lateral (select count(*) n from owned_volumes ov
                                    where ov.collection_edition_id = ce.id) o
                where ce.user_id = :userId
                """)
            .param("userId", userId)
            .query((rs, i) ->
                new long[] {
                  rs.getLong("editions"),
                  rs.getLong("series"),
                  rs.getLong("owned"),
                  rs.getLong("total"),
                  rs.getLong("done")
                })
            .single();
    return new CollectionStats(
        totals[0],
        totals[1],
        totals[2],
        totals[3],
        totals[4],
        topPublishers(ownedBy(userId, "e.publisher")),
        inOrder(EditionType.values(), ownedBy(userId, "e.type")),
        inOrder(EditionStatus.values(), ownedBy(userId, "e.status")));
  }

  /** Owned volumes per value of an editions column (a constant from this class, never input). */
  private Map<String, Long> ownedBy(UUID userId, String column) {
    var result = new HashMap<String, Long>();
    jdbc.sql(
            "select "
                + column
                + " k, count(ov.id) volumes from collection_editions ce"
                + " join editions e on e.id = ce.edition_id"
                + " left join owned_volumes ov on ov.collection_edition_id = ce.id"
                + " where ce.user_id = :userId group by "
                + column)
        .param("userId", userId)
        .query(rs -> {
          result.put(rs.getString("k"), rs.getLong("volumes"));
        });
    return result;
  }

  private static List<StatBar> inOrder(Enum<?>[] values, Map<String, Long> volumes) {
    return Arrays.stream(values)
        .map(v -> new StatBar(v.name(), volumes.getOrDefault(v.name(), 0L)))
        .filter(b -> b.volumes() > 0)
        .toList();
  }

  /** Top 5 publishers by volumes (ties alphabetical), then the rest and no-publisher as "Altri". */
  static List<StatBar> topPublishers(Map<String, Long> volumes) {
    var named =
        volumes.entrySet().stream()
            .filter(e -> e.getKey() != null && e.getValue() > 0)
            .sorted(
                Map.Entry.<String, Long>comparingByValue()
                    .reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
            .map(e -> new StatBar(e.getKey(), e.getValue()))
            .toList();
    var bars = new ArrayList<>(named.subList(0, Math.min(5, named.size())));
    long others =
        named.stream().skip(5).mapToLong(StatBar::volumes).sum() + volumes.getOrDefault(null, 0L);
    if (others > 0) {
      bars.add(new StatBar(null, others));
    }
    return List.copyOf(bars);
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

  /** Idempotent: adds the edition to the collection if missing, then the volume if missing. */
  @Transactional
  public void addOwned(UUID userId, UUID editionId, int volume) {
    var ce = ensure(userId, editionId);
    if (!volumes.existsByCollectionEditionIdAndNumber(ce.getId(), volume)) {
      volumes.saveAndFlush(new OwnedVolume(ce.getId(), volume, Instant.now()));
    }
  }

  /** No-op when the volume is not owned; the edition stays in the collection. */
  @Transactional
  public void removeOwned(UUID userId, UUID editionId, int volume) {
    collection
        .findByUserIdAndEditionId(userId, editionId)
        .ifPresent(ce -> volumes.deleteByCollectionEditionIdAndNumber(ce.getId(), volume));
  }

  private CollectionEdition ensure(UUID userId, UUID editionId) {
    return collection
        .findByUserIdAndEditionId(userId, editionId)
        .orElseGet(
            () -> collection.saveAndFlush(new CollectionEdition(userId, editionId, Instant.now())));
  }

  CollectionEntry entry(UUID userId, UUID editionId) {
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
               e.volume_count, e.cover_file is not null has_cover, e.version,
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
                  rs.getLong("version"),
                  List.copyOf(owned));
            })
        .list();
  }
}
