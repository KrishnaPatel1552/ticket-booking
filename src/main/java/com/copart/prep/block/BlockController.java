package com.copart.prep.block;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockController {

    private final BlockSeat blockSeat;

    public BlockController(BlockSeat blockSeat) {
        this.blockSeat = blockSeat;
    }

    @PostMapping("/api/admin/events/{eventId}/seats/{seatId}/block")
    public void block(
            @PathVariable long eventId,
            @PathVariable long seatId,
            @RequestBody OverrideReason request) {
        blockSeat.block(eventId, seatId, request.reason());
    }
}
