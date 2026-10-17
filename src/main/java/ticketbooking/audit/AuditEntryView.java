package ticketbooking.audit;

import java.time.Instant;

public record AuditEntryView(Long id, AuditKind kind, String reason, Long seatId, Instant recordedAt) {
}
