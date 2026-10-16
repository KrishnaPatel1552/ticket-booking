package com.copart.prep.block;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatBlockRepository extends JpaRepository<SeatBlock, Long> {

    List<SeatBlock> findByEventIdAndActiveTrue(Long eventId);

    Optional<SeatBlock> findByEventIdAndSeatIdAndActiveTrue(Long eventId, Long seatId);
}
