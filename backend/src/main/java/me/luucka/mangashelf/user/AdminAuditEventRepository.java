package me.luucka.mangashelf.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {

    List<AdminAuditEvent> findTop100ByOrderByCreatedAtDescIdDesc();
}
