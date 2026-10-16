package com.copart.prep.venue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findByVenueIdOrderByIdAsc(Long venueId);
}
