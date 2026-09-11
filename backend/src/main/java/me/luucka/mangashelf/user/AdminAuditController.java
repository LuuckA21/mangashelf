package me.luucka.mangashelf.user;

import me.luucka.mangashelf.user.dto.AdminAuditEventResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only administrative view of the most recent account changes. */
@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {

    private final AdminAuditService audit;

    public AdminAuditController(AdminAuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AdminAuditEventResponse> recentEvents() {
        return audit.recentEvents().stream()
                .map(AdminAuditEventResponse::from)
                .toList();
    }
}
