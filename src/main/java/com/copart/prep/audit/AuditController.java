package com.copart.prep.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuditEntries auditEntries;

    public AuditController(AuditEntries auditEntries) {
        this.auditEntries = auditEntries;
    }

    @GetMapping("/api/admin/events/{eventId}/audit-entries")
    public List<AuditEntryView> list(@PathVariable long eventId) {
        return auditEntries.list(eventId);
    }
}
