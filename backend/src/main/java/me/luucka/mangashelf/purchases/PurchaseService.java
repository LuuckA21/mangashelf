package me.luucka.mangashelf.purchases;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.luucka.mangashelf.catalog.EditionService;
import me.luucka.mangashelf.common.ConflictException;
import me.luucka.mangashelf.common.FieldValidationException;
import me.luucka.mangashelf.common.NotFoundException;
import me.luucka.mangashelf.purchases.dto.ItemPatch;
import me.luucka.mangashelf.purchases.dto.ItemRequest;
import me.luucka.mangashelf.purchases.dto.ItemView;
import me.luucka.mangashelf.purchases.dto.ListDetail;
import me.luucka.mangashelf.purchases.dto.ListPatch;
import me.luucka.mangashelf.purchases.dto.ListRequest;
import me.luucka.mangashelf.purchases.dto.ListSummary;
import me.luucka.mangashelf.purchases.dto.PurchaseSettings;
import me.luucka.mangashelf.users.AppUser;
import me.luucka.mangashelf.users.AppUserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {

  private static final String OVER = "Lo sconto supera il subtotale";

  private final AppUserRepository users;
  private final PurchaseListRepository lists;
  private final PurchaseItemRepository items;
  private final EditionService editions;
  private final JdbcClient jdbc;

  public PurchaseService(
      AppUserRepository users,
      PurchaseListRepository lists,
      PurchaseItemRepository items,
      EditionService editions,
      JdbcClient jdbc) {
    this.users = users;
    this.lists = lists;
    this.items = items;
    this.editions = editions;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<ListSummary> lists(UUID userId) {
    int percent = users.findById(userId).orElseThrow().getDiscountPercent();
    return jdbc.sql(
            """
            select l.id, l.description, l.month, l.year, l.closed, l.discount_cents, l.points_discount_cents,
                   count(i.id) n, count(i.id) filter (where not i.booked) unbooked,
                   coalesce(sum(i.price_chf_cents), 0) subtotal
            from purchase_lists l left join purchase_items i on i.list_id = l.id
            where l.user_id = :userId
            group by l.id
            order by l.year desc, l.month desc, l.created_at desc
            """)
        .param("userId", userId)
        .query((rs, i) -> {
          long sub = rs.getLong("subtotal");
          long fixed = rs.getLong("discount_cents");
          long discount = rs.wasNull() ? Money.discount(sub, percent) : fixed;
          return new ListSummary(
              rs.getString("id"),
              rs.getString("description"),
              rs.getInt("month"),
              rs.getInt("year"),
              rs.getBoolean("closed"),
              rs.getInt("n"),
              rs.getInt("unbooked"),
              total(sub, discount, rs.getLong("points_discount_cents")));
        })
        .list();
  }

  @Transactional
  public ListDetail create(UUID userId, ListRequest r) {
    var l =
        lists.save(
            new PurchaseList(userId, r.description().trim(), r.month(), r.year(), Instant.now()));
    return detail(userId, l);
  }

  @Transactional(readOnly = true)
  public ListDetail get(UUID userId, UUID id) {
    return detail(userId, require(userId, id));
  }

  @Transactional
  public ListDetail update(UUID userId, UUID id, ListPatch p) {
    var l = require(userId, id);
    boolean auto = Boolean.TRUE.equals(p.discountAuto());
    l.update(
        p.description() == null ? null : p.description().trim(),
        p.month(),
        p.year(),
        p.discountCents(),
        auto,
        p.pointsDiscountCents(),
        Instant.now());
    var d = detail(userId, l);
    // Only saving a discount can trip this; removing rows later just clamps the total at 0.
    boolean fixedSent = p.discountCents() != null && !auto;
    boolean pointsSent = p.pointsDiscountCents() != null;
    if ((fixedSent || pointsSent)
        && d.subtotalCents() - d.discountCents() - d.pointsDiscountCents() < 0) {
      boolean blamePoints =
          pointsSent && (!fixedSent || d.subtotalCents() - d.discountCents() >= 0);
      throw new FieldValidationException(
          blamePoints ? "pointsDiscountCents" : "discountCents", OVER);
    }
    return d;
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    var l = require(userId, id);
    if (l.isClosed()) {
      throw new ConflictException("Una lista chiusa non si può eliminare");
    }
    lists.delete(l);
  }

  @Transactional
  public ListDetail addItem(UUID userId, UUID id, ItemRequest r) {
    var l = require(userId, id);
    editions.require(r.editionId());
    items.save(
        new PurchaseItem(
            l.getId(),
            r.editionId(),
            r.volume(),
            r.releaseDate(),
            r.priceEurCents(),
            r.priceChfCents(),
            Boolean.TRUE.equals(r.booked()),
            Instant.now()));
    items.flush();
    return detail(userId, l);
  }

  @Transactional
  public ListDetail updateItem(UUID userId, UUID id, UUID itemId, ItemPatch p) {
    var l = require(userId, id);
    var item = requireItem(l, itemId);
    if (p.editionId() != null) editions.require(p.editionId());
    item.update(
        p.editionId(),
        p.volume(),
        p.releaseDate(),
        Boolean.TRUE.equals(p.clearReleaseDate()),
        p.priceEurCents(),
        p.priceChfCents(),
        p.booked(),
        Instant.now());
    items.flush();
    return detail(userId, l);
  }

  @Transactional
  public ListDetail deleteItem(UUID userId, UUID id, UUID itemId) {
    var l = require(userId, id);
    items.delete(requireItem(l, itemId));
    items.flush();
    return detail(userId, l);
  }

  private PurchaseList require(UUID userId, UUID id) {
    return lists
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Lista non trovata"));
  }

  private PurchaseItem requireItem(PurchaseList l, UUID itemId) {
    return items
        .findByIdAndListId(itemId, l.getId())
        .orElseThrow(() -> new NotFoundException("Riga non trovata"));
  }

  private static long total(long subtotal, long discount, long points) {
    return Math.max(0, subtotal - discount - points);
  }

  private ListDetail detail(UUID userId, PurchaseList l) {
    var rows =
        jdbc.sql(
                """
                select i.id, i.edition_id, s.id series_id, s.title, e.name, e.volume_count,
                       i.volume, i.release_date, i.price_eur_cents, i.price_chf_cents, i.booked
                from purchase_items i
                join editions e on e.id = i.edition_id
                join series s on s.id = e.series_id
                where i.list_id = :listId
                order by i.release_date asc nulls last, lower(s.title), lower(e.name), i.volume
                """)
            .param("listId", l.getId())
            .query((rs, n) ->
                new ItemView(
                    rs.getString("id"),
                    rs.getString("edition_id"),
                    rs.getString("series_id"),
                    rs.getString("title"),
                    rs.getString("name"),
                    rs.getInt("volume_count"),
                    rs.getInt("volume"),
                    rs.getObject("release_date", java.time.LocalDate.class),
                    rs.getLong("price_eur_cents"),
                    rs.getLong("price_chf_cents"),
                    rs.getBoolean("booked"),
                    List.of()))
            .list();
    long sub = rows.stream().mapToLong(ItemView::priceChfCents).sum();
    boolean auto = l.getDiscountCents() == null;
    long discount =
        auto
            ? Money.discount(sub, users.findById(userId).orElseThrow().getDiscountPercent())
            : l.getDiscountCents();
    return new ListDetail(
        l.getId().toString(),
        l.getDescription(),
        l.getMonth(),
        l.getYear(),
        l.isClosed(),
        rows,
        sub,
        discount,
        auto,
        l.getPointsDiscountCents(),
        total(sub, discount, l.getPointsDiscountCents()));
  }

  @Transactional(readOnly = true)
  public PurchaseSettings settings(UUID userId) {
    return toDto(users.findById(userId).orElseThrow());
  }

  @Transactional
  public PurchaseSettings updateSettings(UUID userId, PurchaseSettings s) {
    var user = users.findById(userId).orElseThrow();
    user.setPurchaseSettings(s.chfRate(), s.discountPercent());
    return toDto(user);
  }

  private static PurchaseSettings toDto(AppUser u) {
    return new PurchaseSettings(u.getChfRate(), u.getDiscountPercent());
  }
}
