package com.copart.prep.event;

import java.util.List;

public record SectionSeats(String name, List<SeatView> seats) {
}
