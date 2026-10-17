package ticketbooking.waitlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByEventIdAndCustomerId(long eventId, long customerId);

    Optional<WaitlistEntry> findByEventIdAndCustomerIdAndEndedAtIsNull(long eventId, long customerId);

    List<WaitlistEntry> findByEventIdOrderByIdAsc(long eventId);

    List<WaitlistEntry> findByEventIdAndEndedAtIsNullOrderByIdAsc(long eventId);

    long countByEventIdAndIdLessThanEqual(long eventId, long id);

    long countByEventIdAndEndedAtIsNullAndIdLessThanEqual(long eventId, long id);
}
