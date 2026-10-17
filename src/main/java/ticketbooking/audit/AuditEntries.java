package ticketbooking.audit;

import ticketbooking.event.EventRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditEntries {

    private final EventRepository events;
    private final AuditEntryRepository auditEntries;

    public AuditEntries(EventRepository events, AuditEntryRepository auditEntries) {
        this.events = events;
        this.auditEntries = auditEntries;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public List<AuditEntryView> list(long eventId) {
        if (!events.existsById(eventId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return auditEntries.findByEventIdOrderByIdAsc(eventId).stream()
                .map(entry -> new AuditEntryView(
                        entry.getId(),
                        entry.getKind(),
                        entry.getReason(),
                        entry.getSeat() == null ? null : entry.getSeat().getId(),
                        entry.getRecordedAt()))
                .toList();
    }
}
