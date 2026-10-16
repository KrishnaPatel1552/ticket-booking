package com.copart.prep.hold;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    List<EventSeat> findByEventIdAndActiveTrue(Long eventId);
}
