package me.luucka.mangashelf.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    /**
     * Every line this user has bought, highest and latest purchase last.
     *
     * <p>Feeds the suggestions: what to buy next is read from what was
     * bought before, so planned lines must not advance the proposed number.
     * The whole purchase history is needed rather than one list.
     * Edition, work and list are fetched along, because each suggestion
     * names all three — left lazy they would cost three queries per row.
     */
    @Query("""
            SELECT i FROM PurchaseItem i
            JOIN FETCH i.series s
            JOIN FETCH s.manga
            JOIN FETCH i.list l
            WHERE l.user.id = :userId
              AND i.purchasedAt IS NOT NULL
            ORDER BY i.volumeNumber ASC, i.purchasedAt ASC, i.id ASC
            """)
    List<PurchaseItem> findPurchasedByUser(@Param("userId") Long userId);

    /**
     * How many purchase lines name this edition, across every user.
     *
     * <p>Guards deletion of the edition: the foreign key cascades, so
     * removing a run would silently strip lines from other people's lists.
     */
    long countBySeriesId(Long seriesId);

    /** Same guard, widened to every edition of a work. */
    @Query("SELECT COUNT(i) FROM PurchaseItem i WHERE i.series.manga.id = :mangaId")
    long countByMangaId(@Param("mangaId") Long mangaId);
}
