package me.luucka.mangashelf.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    /**
     * Every line this user has ever written, newest number last.
     *
     * <p>Feeds the suggestions: what to buy next is read from what was
     * bought before, so the whole history is needed rather than one list.
     * Edition, work and list are fetched along, because each suggestion
     * names all three — left lazy they would cost three queries per row.
     */
    @Query("""
            SELECT i FROM PurchaseItem i
            JOIN FETCH i.series s
            JOIN FETCH s.manga
            JOIN FETCH i.list l
            WHERE l.user.id = :userId
            ORDER BY i.volumeNumber ASC
            """)
    List<PurchaseItem> findAllByUser(@Param("userId") Long userId);
}
