package ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;

import ticketbooking.checkout.PausingPaymentAttemptGate;
import ticketbooking.promo.PausingPromoCodeLockGate;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PriceApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PausingPaymentAttemptGate paymentGate;

    @Autowired
    private PausingPromoCodeLockGate promoLockGate;

    @Autowired
    private DataSource dataSource;

    @Test
    void twoSeatsInsideTheEarlyBirdWindowPriceAtTheEarlyBirdPricePlusTheFee() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1, a2)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(8100);
    }

    @Test
    void theEarlyBirdWindowIncludesItsStartAndExcludesItsEnd() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();

        assertThat(checkoutOne(ada, eventId, opened, "A1", "2026-10-01T00:00:00Z")).isEqualTo(4100);
        assertThat(checkoutOne(ada, eventId, opened, "A2", "2026-11-14T23:59:59Z")).isEqualTo(4100);
        assertThat(checkoutOne(ada, eventId, opened, "A3", "2026-09-30T23:59:59Z")).isEqualTo(5100);
        assertThat(checkoutOne(ada, eventId, opened, "A4", "2026-11-15T00:00:00Z")).isEqualTo(5100);
    }

    @Test
    void fourSeatsInsideTheEarlyBirdWindowTakeTheGroupRateOffOnce() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> seats = List.of(seatId(opened, "A1"), seatId(opened, "A2"), seatId(opened, "A3"), seatId(opened, "A4"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", seats));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seats,
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(15600);
    }

    @Test
    void threeSeatsInsideTheEarlyBirdWindowDoNotTakeTheGroupRate() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> seats = List.of(seatId(opened, "A1"), seatId(opened, "A2"), seatId(opened, "A3"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", seats));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seats,
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(12100);
    }

    @Test
    void anAdminCreatesAPromoCodeAndACustomerIsRefused() {
        ApiSession admin = signIn("admin@example.com");
        String code = "SAVE100-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "amountCents", 100,
                "globalLimit", 5,
                "perCustomerLimit", 1,
                "exclusive", false));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("code").asText()).isEqualTo(code);
        assertThat(created.getBody().get("amountCents").asInt()).isEqualTo(100);
        assertThat(created.getBody().get("globalLimit").asInt()).isEqualTo(5);
        assertThat(created.getBody().get("perCustomerLimit").asInt()).isEqualTo(1);
        assertThat(created.getBody().get("exclusive").asBoolean()).isFalse();

        ApiSession ada = signIn("ada@example.com");
        ResponseEntity<JsonNode> refused = ada.post("/api/admin/promo-codes", java.util.Map.of(
                "code", "NOPE-" + UUID.randomUUID(),
                "amountCents", 50,
                "globalLimit", 1,
                "perCustomerLimit", 1,
                "exclusive", true));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aPromoCodeComesOffTheTicketTotalAndLeavesTheServiceFee() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 100, 5, 2, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> attached = ada.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of(
                "code", code));

        assertThat(attached.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attached.getBody().get("promoCode").asText()).isEqualTo(code);
        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(5000);
    }

    @Test
    void aSecondPromoCodeIsRejectedAndTheFirstCodeStays() {
        ApiSession admin = signIn("admin@example.com");
        String first = createPromo(admin, 100, 5, 2, false);
        String second = createPromo(admin, 300, 5, 2, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of("code", first)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> rejected = ada.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of(
                "code", second));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("promoCode").asText()).isEqualTo(first);
        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(5000);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("promoCode").asText()).isEqualTo(first);
    }

    @Test
    void anExclusivePromoCodeUsesThePriceTierAndDropsTheGroupRate() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 1000, 5, 2, true);
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> seats = List.of(
                seatId(opened, "A1"),
                seatId(opened, "A2"),
                seatId(opened, "A3"),
                seatId(opened, "A4"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", seats));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seats,
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(19100);
        List<JsonNode> tickets = ticketsById(order);
        assertThat(tickets).hasSize(4);
        int netsPlusFee = 0;
        for (JsonNode ticket : tickets) {
            assertThat(ticket.get("startingPriceCents").asInt()).isEqualTo(5000);
            assertThat(ticket.get("groupShareCents").asInt()).isEqualTo(0);
            assertThat(ticket.get("promoShareCents").asInt()).isEqualTo(250);
            assertThat(ticket.get("feeShareCents").asInt()).isEqualTo(25);
            netsPlusFee += 5000 - 250 + 25;
        }
        assertThat(netsPlusFee).isEqualTo(19100);
    }

    @Test
    void aPromoCodeWithNoUseLeftIsSkippedAndCheckoutStillSucceeds() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200, 1, 1, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        long adaOrder = holdAttachAndCheckout(ada, eventId, "A1", code, "success");
        assertThat(amount(ada, adaOrder)).isEqualTo(4900);

        long graceOrder = holdAttachAndCheckout(grace, eventId, "A2", code, "success");

        assertThat(amount(grace, graceOrder)).isEqualTo(5100);
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("promoCode").asText()).isEqualTo(code);
    }

    @Test
    void aDeclinedPaymentDoesNotConsumeThePromoUse() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200, 1, 1, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        long adaOrder = holdAttachAndCheckout(ada, eventId, "A1", code, "decline");
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("declined");
        assertThat(amount(ada, adaOrder)).isEqualTo(4900);

        long graceOrder = holdAttachAndCheckout(grace, eventId, "A1", code, "success");

        assertThat(amount(grace, graceOrder)).isEqualTo(4900);
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
    }

    @Test
    void anUnknownPaymentCountsAsATakenPromoUse() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200, 1, 1, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        long adaOrder = holdAttachAndCheckout(ada, eventId, "A1", code, "timeout");
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("unknown");

        long graceOrder = holdAttachAndCheckout(grace, eventId, "A2", code, "success");

        assertThat(amount(grace, graceOrder)).isEqualTo(5100);
    }

    @Test
    void aPendingPaymentCountsAsATakenPromoUse() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200, 1, 1, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long adaOrder = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + adaOrder + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        paymentGate.arm();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<JsonNode>> checkout = pool.submit(() -> ada.post(
                    "/api/orders/" + adaOrder + "/checkout",
                    java.util.Map.of("seatIds", List.of(a1), "mock", "success")));
            assertThat(paymentGate.awaitArrival(Duration.ofSeconds(10))).isTrue();
            long graceOrder = holdAttachAndCheckout(grace, eventId, "A2", code, "success");
            assertThat(amount(grace, graceOrder)).isEqualTo(5100);
            assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("status").asText())
                    .isEqualTo("pending");
            paymentGate.release();
            assertThat(checkout.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            paymentGate.release();
            pool.shutdownNow();
        }

        assertThat(amount(ada, adaOrder)).isEqualTo(4900);
    }

    @Test
    void thePerCustomerLimitStopsASecondOrderFromTheSameCustomer() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200, 5, 1, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        long first = holdAttachAndCheckout(ada, eventId, "A1", code, "success");
        assertThat(amount(ada, first)).isEqualTo(4900);

        long second = holdAttachAndCheckout(ada, eventId, "A2", code, "success");
        long graceOrder = holdAttachAndCheckout(grace, eventId, "A3", code, "success");

        assertThat(amount(ada, second)).isEqualTo(5100);
        assertThat(amount(grace, graceOrder)).isEqualTo(4900);
    }

    @Test
    void aPromoDiscountStopsAtTheTicketTotalAndLeavesTheServiceFee() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 9000, 5, 2, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        long orderId = holdAttachAndCheckout(ada, eventId, "A1", code, "success");

        assertThat(amount(ada, orderId)).isEqualTo(100);
    }

    @Test
    void aUsedUpExclusiveCodeIsSkippedAndEarlyBirdAndTheGroupRateStand() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 1000, 1, 1, true);
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> adaSeats = List.of(
                seatId(opened, "A1"), seatId(opened, "A2"), seatId(opened, "A3"), seatId(opened, "A4"));
        List<Long> graceSeats = List.of(
                seatId(opened, "A5"), seatId(opened, "A6"), seatId(opened, "A7"), seatId(opened, "A8"));
        ResponseEntity<JsonNode> adaHeld = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", adaSeats));
        assertThat(adaHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        long adaOrder = adaHeld.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + adaOrder + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(ada.post("/api/orders/" + adaOrder + "/checkout", java.util.Map.of(
                "seatIds", adaSeats, "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(amount(ada, adaOrder)).isEqualTo(19100);

        ResponseEntity<JsonNode> graceHeld = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", graceSeats));
        assertThat(graceHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        long graceOrder = graceHeld.getBody().get("orderId").asLong();
        assertThat(grace.post("/api/orders/" + graceOrder + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(grace.post("/api/orders/" + graceOrder + "/checkout", java.util.Map.of(
                "seatIds", graceSeats, "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(amount(grace, graceOrder)).isEqualTo(15600);
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
    }

    @Test
    void threeTicketsSplitAHundredCentFeeWithTheLeftoverOnTheLowestTicketId() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> seats = List.of(seatId(opened, "A1"), seatId(opened, "A2"), seatId(opened, "A3"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", seats));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seats,
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(15100);
        List<JsonNode> tickets = ticketsById(order);
        assertThat(tickets).hasSize(3);
        assertThat(tickets.get(0).get("feeShareCents").asInt()).isEqualTo(34);
        assertThat(tickets.get(1).get("feeShareCents").asInt()).isEqualTo(33);
        assertThat(tickets.get(2).get("feeShareCents").asInt()).isEqualTo(33);
        int netsPlusFee = 0;
        for (JsonNode ticket : tickets) {
            assertThat(ticket.get("startingPriceCents").asInt()).isEqualTo(5000);
            assertThat(ticket.get("groupShareCents").asInt()).isEqualTo(0);
            assertThat(ticket.get("promoShareCents").asInt()).isEqualTo(0);
            netsPlusFee += ticket.get("startingPriceCents").asInt()
                    - ticket.get("groupShareCents").asInt()
                    - ticket.get("promoShareCents").asInt()
                    + ticket.get("feeShareCents").asInt();
        }
        assertThat(netsPlusFee).isEqualTo(15100);
    }

    @Test
    void aHundredCentPromoSplitsWithTheLeftoverOnTheLowestTicketId() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 100, 5, 2, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> seats = List.of(seatId(opened, "A1"), seatId(opened, "A2"), seatId(opened, "A3"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", seats));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seats, "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(15000);
        List<JsonNode> tickets = ticketsById(order);
        assertThat(tickets.get(0).get("promoShareCents").asInt()).isEqualTo(34);
        assertThat(tickets.get(1).get("promoShareCents").asInt()).isEqualTo(33);
        assertThat(tickets.get(2).get("promoShareCents").asInt()).isEqualTo(33);
        assertThat(tickets.get(0).get("feeShareCents").asInt()).isEqualTo(34);
        assertThat(tickets.get(1).get("feeShareCents").asInt()).isEqualTo(33);
        assertThat(tickets.get(2).get("feeShareCents").asInt()).isEqualTo(33);
    }

    @Test
    void aLaterSuccessKeepsTheSharesRecordedWithThePendingPayment() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        paymentGate.arm();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<JsonNode>> checkout = pool.submit(() -> ada.post(
                    "/api/orders/" + orderId + "/checkout",
                    java.util.Map.of("seatIds", List.of(a1), "mock", "success")));
            assertThat(paymentGate.awaitArrival(Duration.ofSeconds(10))).isTrue();
            assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-11-15T00:00:00Z")).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            paymentGate.release();
            assertThat(checkout.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            paymentGate.release();
            pool.shutdownNow();
        }

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        JsonNode ticket = ticketsById(order).get(0);
        assertThat(ticket.get("startingPriceCents").asInt()).isEqualTo(4000);
        assertThat(ticket.get("groupShareCents").asInt()).isEqualTo(0);
        assertThat(ticket.get("promoShareCents").asInt()).isEqualTo(0);
        assertThat(ticket.get("feeShareCents").asInt()).isEqualTo(100);
    }

    @Test
    void theLastPromoUseGoesToTheCheckoutThatLocksTheCodeFirst() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200, 1, 1, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> adaHeld = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        ResponseEntity<JsonNode> graceHeld = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a2)));
        assertThat(adaHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(graceHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        long adaOrder = adaHeld.getBody().get("orderId").asLong();
        long graceOrder = graceHeld.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + adaOrder + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(grace.post("/api/orders/" + graceOrder + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        promoLockGate.arm();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<JsonNode>> first = pool.submit(() -> ada.post(
                    "/api/orders/" + adaOrder + "/checkout",
                    java.util.Map.of("seatIds", List.of(a1), "mock", "success")));
            assertThat(promoLockGate.awaitFirstArrival(Duration.ofSeconds(10))).isTrue();
            Future<ResponseEntity<JsonNode>> second = pool.submit(() -> grace.post(
                    "/api/orders/" + graceOrder + "/checkout",
                    java.util.Map.of("seatIds", List.of(a2), "mock", "success")));
            awaitSecondSessionWaitingOnThePromoLock();
            promoLockGate.release();
            assertThat(first.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            promoLockGate.release();
            pool.shutdownNow();
        }

        assertThat(List.of(amount(ada, adaOrder), amount(grace, graceOrder))).containsExactlyInAnyOrder(4900, 5100);
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
    }

    private void awaitSecondSessionWaitingOnThePromoLock() throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (promoLockSessions()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("The second checkout never waited on the promo code lock");
    }

    private boolean promoLockSessions() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select "
                                + "count(*) filter (where state = 'idle in transaction') as idle, "
                                + "count(*) filter (where wait_event_type = 'Lock') as waiting "
                                + "from pg_stat_activity "
                                + "where datname = current_database() "
                                + "and pid <> pg_backend_pid()")) {
            if (!rows.next()) {
                return false;
            }
            return rows.getInt("idle") >= 1 && rows.getInt("waiting") >= 1;
        }
    }

    private static List<JsonNode> ticketsById(JsonNode order) {
        List<JsonNode> tickets = new ArrayList<>();
        order.get("tickets").forEach(tickets::add);
        tickets.sort(Comparator.comparingLong(ticket -> ticket.get("id").asLong()));
        return tickets;
    }

    private long holdAttachAndCheckout(ApiSession customer, long eventId, String label, String code, String mock) {
        JsonNode opened = customer.get("/api/events/" + eventId).getBody();
        long seat = seatId(opened, label);
        ResponseEntity<JsonNode> held = customer.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seat)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(customer.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> checkedOut = customer.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(seat),
                "mock", mock));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        return orderId;
    }

    private static int amount(ApiSession customer, long orderId) {
        return customer.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt();
    }

    private String createPromo(ApiSession admin, int amountCents, int globalLimit, int perCustomerLimit, boolean exclusive) {
        String code = "PROMO-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "amountCents", amountCents,
                "globalLimit", globalLimit,
                "perCustomerLimit", perCustomerLimit,
                "exclusive", exclusive));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    private long createPlainEvent(ApiSession admin) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "PriceNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-06-01T01:00:00Z",
                "timeZone", "America/New_York"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return findNamed(admin.get("/api/events?name=" + name).getBody(), name).get("id").asLong();
    }

    private int checkoutOne(ApiSession ada, long eventId, JsonNode opened, String label, String instant) {
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", instant)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        long seat = seatId(opened, label);
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seat)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(seat),
                "mock", "success"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt();
    }

    private long createEvent(ApiSession admin, String earlyBirdStartsAt, String earlyBirdEndsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "PriceNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-06-01T01:00:00Z",
                "timeZone", "America/New_York",
                "earlyBirdStartsAt", earlyBirdStartsAt,
                "earlyBirdEndsAt", earlyBirdEndsAt));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return findNamed(admin.get("/api/events?name=" + name).getBody(), name).get("id").asLong();
    }

    private static JsonNode findNamed(JsonNode events, String name) {
        for (JsonNode event : events) {
            if (name.equals(event.get("name").asText())) {
                return event;
            }
        }
        throw new AssertionError("No event named " + name);
    }

    private static long seatId(JsonNode event, String label) {
        for (JsonNode section : event.get("sections")) {
            for (JsonNode seat : section.get("seats")) {
                if (label.equals(seat.get("label").asText())) {
                    return seat.get("id").asLong();
                }
            }
        }
        throw new AssertionError("No seat labeled " + label);
    }

    private ApiSession signIn(String email) {
        ApiSession session = new ApiSession();
        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", email,
                "password", "password"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return session;
    }

    private final class ApiSession {
        private String cookieHeader = "";

        private ResponseEntity<JsonNode> post(String path, Object body) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!cookieHeader.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, cookieHeader);
            }
            ResponseEntity<JsonNode> response = rest.exchange(
                    path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            remember(response);
            return response;
        }

        private ResponseEntity<JsonNode> get(String path) {
            HttpHeaders headers = new HttpHeaders();
            if (!cookieHeader.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, cookieHeader);
            }
            return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        }

        private void remember(ResponseEntity<?> response) {
            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (setCookies == null || setCookies.isEmpty()) {
                return;
            }
            cookieHeader = setCookies.stream()
                    .map(cookie -> cookie.split(";", 2)[0])
                    .collect(Collectors.joining("; "));
        }
    }
}
