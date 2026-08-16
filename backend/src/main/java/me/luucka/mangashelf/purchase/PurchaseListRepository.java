package me.luucka.mangashelf.purchase;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseListRepository extends JpaRepository<PurchaseList, Long> {

    /**
     * The index still needs every line, because a list is summarised by its
     * total. Without the graph that is one query per list, plus one per row.
     */
    @EntityGraph(attributePaths = "items")
    List<PurchaseList> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Loads the lines and the editions they name.
     *
     * <p>A list page shows the edition of every row, and the DTO is built
     * once the transaction has closed: without the graph each row would
     * trigger its own query, or fail outright.
     */
    @EntityGraph(attributePaths = {"items", "items.series", "items.series.manga"})
    Optional<PurchaseList> findWithItemsByIdAndUserId(Long id, Long userId);
}
