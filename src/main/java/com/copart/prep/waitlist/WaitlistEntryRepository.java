package com.copart.prep.waitlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByEventIdAndCustomerId(long eventId, long customerId);

    List<WaitlistEntry> findByEventIdOrderByIdAsc(long eventId);

    long countByEventIdAndIdLessThanEqual(long eventId, long id);
}
