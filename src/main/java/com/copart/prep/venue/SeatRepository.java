package com.copart.prep.venue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findBySectionIdOrderByIdAsc(Long sectionId);

    long countBySection_Venue_Id(Long venueId);
}
