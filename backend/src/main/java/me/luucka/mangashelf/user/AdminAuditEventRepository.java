package me.luucka.mangashelf.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {

    @Modifying
    @Query("update AdminAuditEvent e set e.actorUsername = '[deleted]' where e.actorUserId = :id")
    void anonymizeActor(@Param("id") Long id);

    @Modifying
    @Query("update AdminAuditEvent e set e.targetUsername = '[deleted]' where e.targetUserId = :id")
    void anonymizeTarget(@Param("id") Long id);

    List<AdminAuditEvent> findTop100ByOrderByCreatedAtDescIdDesc();
}
