package ticketbooking.sales;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SalesController {

    private final SalesOccupancy salesOccupancy;

    public SalesController(SalesOccupancy salesOccupancy) {
        this.salesOccupancy = salesOccupancy;
    }

    @GetMapping("/api/admin/events/{eventId}/sales")
    public SalesView read(@PathVariable long eventId) {
        return salesOccupancy.read(eventId);
    }
}
