package com.copart.prep;

import static org.assertj.core.api.Assertions.assertThat;

import com.copart.prep.checkout.PausingPaymentAttemptGate;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
class CheckoutApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PausingPaymentAttemptGate paymentGate;

    @Test
    void submittingCheckoutShowsTicketsAndTheSucceededPayment() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
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
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(ticketLabels(order)).containsExactlyInAnyOrder("A1", "A2");
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("ticket");
        assertThat(seatState(after, "A2")).isEqualTo("ticket");
        assertThat(seatState(after, "A3")).isEqualTo("free");
    }

    @Test
    void declinedCheckoutReleasesTheSeatsAndLeavesNoTicket() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1, a2)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "decline"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("declined");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(ticketLabels(order)).isEmpty();
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("free");
        assertThat(seatState(after, "A2")).isEqualTo("free");

        ResponseEntity<JsonNode> graceHeld = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(graceHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(grace.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("tickets")).isEmpty();
    }

    @Test
    void timeoutAndUnknownKeepTheSeatsPastTenMinutes() {
        unfinishedMockKeepsTheSeat("timeout", "2033-01-01T10:00:00Z", "2033-01-01T10:10:00Z");
        unfinishedMockKeepsTheSeat("unknown", "2033-02-01T10:00:00Z", "2033-02-01T10:10:00Z");
    }

    private void unfinishedMockKeepsTheSeat(String mock, String heldAt, String expiredAt) {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", heldAt)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", mock));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("unknown");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        ResponseEntity<JsonNode> graceBefore = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(graceBefore.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", expiredAt)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        ResponseEntity<JsonNode> graceAfter = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(graceAfter.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ticketLabels(ada.get("/api/orders/" + orderId).getBody())).isEmpty();
    }

    @Test
    void checkoutAfterTheHoldClockWritesNoPayment() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2034-03-01T08:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2034-03-01T08:10:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").isNull()).isTrue();
        assertThat(ticketLabels(ada.get("/api/orders/" + orderId).getBody())).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
    }

    @Test
    void checkoutOfASeatThatIsNotTheCustomersLiveHoldWritesNoPayment() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> adaHeld = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(adaHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = adaHeld.getBody().get("orderId").asLong();
        ResponseEntity<JsonNode> graceHeld = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a2)));
        assertThat(graceHeld.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").isNull()).isTrue();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("held");
        ResponseEntity<JsonNode> onlyHerSeat = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(onlyHerSeat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(seatState(grace.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("held");
    }

    @Test
    void checkoutUsesThePriceTierPlusTheServiceFeeInsideTheEarlyBirdWindow() {
        ApiSession admin = signIn("admin@example.com");
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "EarlyBirdCheckout-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-06-01T01:00:00Z",
                "timeZone", "America/New_York",
                "earlyBirdStartsAt", "2035-01-01T00:00:00Z",
                "earlyBirdEndsAt", "2035-06-01T00:00:00Z"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long eventId = findNamed(admin.get("/api/events?name=" + name).getBody(), name).get("id").asLong();
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2035-03-01T12:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatPrice(opened, "A1")).isEqualTo(5000);
        assertThat(seatEarlyBird(opened, "A1")).isEqualTo(4000);
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(5100);
    }

    @Test
    void aCheckoutLeavesTheNextHoldOnANewOrderThatStillCountsTowardTwelve() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> firstSix = List.of(
                seatId(opened, "A1"),
                seatId(opened, "A2"),
                seatId(opened, "A3"),
                seatId(opened, "A4"),
                seatId(opened, "A5"),
                seatId(opened, "A6"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", firstSix));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long firstOrderId = held.getBody().get("orderId").asLong();
        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + firstOrderId + "/checkout", java.util.Map.of(
                "seatIds", firstSix,
                "mock", "success"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + firstOrderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(30100);

        List<Long> nextSix = List.of(
                seatId(opened, "A7"),
                seatId(opened, "A8"),
                seatId(opened, "A9"),
                seatId(opened, "A10"),
                seatId(opened, "A11"),
                seatId(opened, "A12"));
        ResponseEntity<JsonNode> second = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", nextSix));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("orderId").asLong()).isNotEqualTo(firstOrderId);

        ResponseEntity<JsonNode> thirteenth = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "B1"))));
        assertThat(thirteenth.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("ticket");
        assertThat(seatState(after, "A12")).isEqualTo("held");
        assertThat(seatState(after, "B1")).isEqualTo("free");
    }

    @Test
    void openingTheOrderLooksUpAnUnknownPaymentOnceAndWritesTheTickets() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "unknown"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> lookedUp = ada.get("/api/orders/" + orderId + "?lookup=success");

        assertThat(lookedUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookedUp.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(lookedUp.getBody().get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(lookedUp.getBody())).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        JsonNode again = ada.get("/api/orders/" + orderId + "?lookup=decline").getBody();
        assertThat(again.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(again)).containsExactly("A1");
    }

    @Test
    void aDeclinedLookupReleasesTheSeatsAndAnUnknownLookupLeavesThem() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1, a2)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode stillUnknown = ada.get("/api/orders/" + orderId + "?lookup=unknown").getBody();
        assertThat(stillUnknown.get("payment").get("status").asText()).isEqualTo("unknown");
        assertThat(ticketLabels(stillUnknown)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");

        JsonNode declined = ada.get("/api/orders/" + orderId + "?lookup=decline").getBody();
        assertThat(declined.get("payment").get("status").asText()).isEqualTo("declined");
        assertThat(declined.get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(ticketLabels(declined)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("free");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1))).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reconcileWritesTicketsForAnUnknownPaymentAndForASucceededPaymentThatHasNone() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2036-04-01T09:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> unknownHold = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(unknownHold.getStatusCode()).isEqualTo(HttpStatus.OK);
        long unknownOrderId = unknownHold.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + unknownOrderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> reconciled = ada.post("/api/orders/" + unknownOrderId + "/reconcile", java.util.Map.of(
                "lookup", "success"));
        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reconciled.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(reconciled.getBody().get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(reconciled.getBody())).containsExactly("A1");

        ResponseEntity<JsonNode> crashedHold = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a2)));
        assertThat(crashedHold.getStatusCode()).isEqualTo(HttpStatus.OK);
        long crashedOrderId = crashedHold.getBody().get("orderId").asLong();
        assertThat(crashedOrderId).isNotEqualTo(unknownOrderId);
        insertSucceededPayment(crashedOrderId);

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2036-04-01T09:10:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("held");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a2))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode beforeRepair = ada.get("/api/orders/" + crashedOrderId).getBody();
        assertThat(beforeRepair.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(beforeRepair.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(beforeRepair)).isEmpty();

        ResponseEntity<JsonNode> repaired = ada.post("/api/orders/" + crashedOrderId + "/reconcile", java.util.Map.of(
                "lookup", "success"));
        assertThat(repaired.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repaired.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(repaired.getBody().get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(repaired.getBody())).containsExactly("A2");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("ticket");
        assertThat(ticketLabels(ada.get("/api/orders/" + crashedOrderId + "?lookup=decline").getBody()))
                .containsExactly("A2");
    }

    @Test
    void thePendingPaymentIsVisibleBeforeTheMockReturns() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession watcher = signIn("ada@example.com");
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
            JsonNode during = watcher.get("/api/orders/" + orderId).getBody();
            assertThat(during.get("payment").get("status").asText()).isEqualTo("pending");
            assertThat(during.get("payment").get("amountCents").asInt()).isEqualTo(5100);
            assertThat(ticketLabels(during)).isEmpty();
            assertThat(seatState(watcher.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
            paymentGate.release();
            assertThat(checkout.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            paymentGate.release();
            pool.shutdownNow();
        }

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(order)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
    }

    private void insertSucceededPayment(long orderId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into payment (order_id, amount_cents, status, created_at) values (?, ?, 'SUCCEEDED', ?)")) {
            statement.setLong(1, orderId);
            statement.setInt(2, 5100);
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2036-04-01T09:00:00Z")));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long createEvent(ApiSession admin) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "CheckoutNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-03-01T01:00:00Z",
                "timeZone", "America/New_York"));
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

    private static String seatState(JsonNode event, String label) {
        return seat(event, label).get("state").asText();
    }

    private static int seatPrice(JsonNode event, String label) {
        return seat(event, label).get("priceCents").asInt();
    }

    private static int seatEarlyBird(JsonNode event, String label) {
        return seat(event, label).get("earlyBirdPriceCents").asInt();
    }

    private static JsonNode seat(JsonNode event, String label) {
        for (JsonNode section : event.get("sections")) {
            for (JsonNode candidate : section.get("seats")) {
                if (label.equals(candidate.get("label").asText())) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("No seat labeled " + label);
    }

    private static List<String> ticketLabels(JsonNode order) {
        List<String> labels = new java.util.ArrayList<>();
        for (JsonNode ticket : order.get("tickets")) {
            labels.add(ticket.get("label").asText());
        }
        return labels;
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
