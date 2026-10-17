package ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;

import ticketbooking.checkout.PausingPaymentAttemptGate;
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
class RescheduleApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PausingPaymentAttemptGate paymentGate;

    @Autowired
    private DataSource dataSource;

    @Test
    void anAdminReschedulesTheEventAndTheNextSeatMapShowsTheNewStart() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        assertThat(opened.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(opened, "A1")).isEqualTo("free");

        ResponseEntity<JsonNode> rescheduled = admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date"));

        assertThat(rescheduled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2027-03-01T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        assertThat(seatState(map, "B8")).isEqualTo("free");
        ResponseEntity<JsonNode> entries = admin.get("/api/admin/events/" + eventId + "/audit-entries");
        assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entries.getBody()).hasSize(1);
        assertThat(entries.getBody().get(0).get("kind").asText()).isEqualTo("reschedule");
        assertThat(entries.getBody().get(0).get("reason").asText()).isEqualTo("The venue moved the date");
        assertThat(entries.getBody().get(0).get("seatId").isNull()).isTrue();
        assertThat(entries.getBody().get(0).get("recordedAt").asText()).isEqualTo("2026-10-16T15:00:00Z");
    }

    @Test
    void aCustomerIsRefusedAReschedule() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> refused = ada.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "I cannot attend"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
    }

    @Test
    void aRescheduleWithoutAReasonIsRefused() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String path = "/api/admin/events/" + eventId + "/reschedule";

        assertThat(admin.post(path, java.util.Map.of("startsAt", "2027-03-01T01:00:00Z", "reason", "")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("startsAt", "2027-03-01T01:00:00Z", "reason", "   ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("startsAt", "2027-03-01T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
    }

    @Test
    void aCancelledEventIsRefusedAReschedule() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> refused = admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("blocked");
        JsonNode entries = admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("kind").asText()).isEqualTo("event-cancellation");
    }

    @Test
    void ticketsStayOnTheOrderAndNothingIsRefunded() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        JsonNode before = ada.get("/api/orders/" + orderId).getBody();
        assertThat(before.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(before.get("tickets").get(0).get("startingPriceCents").asInt()).isEqualTo(4000);
        assertThat(before.get("tickets").get(0).get("feeShareCents").asInt()).isEqualTo(100);

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(order)).containsExactly("A1");
        assertThat(order.get("refunds")).isEmpty();
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2027-03-01T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aBareHoldEndsAndNothingIsRefunded() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("holds")).hasSize(1);

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").isNull()).isTrue();
        assertThat(order.get("holds")).isEmpty();
        assertThat(order.get("tickets")).isEmpty();
        assertThat(order.get("refunds")).isEmpty();
        JsonNode map = grace.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2027-03-01T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aFreedHoldIsOfferedAndTheWaitlistKeepsItsOrder() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        blockExcept(admin, eventId, opened, "A1");
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/events/" + eventId + "/waitlist", java.util.Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(grace.post("/api/events/" + eventId + "/waitlist", java.util.Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode adaBefore = ada.get("/api/events/" + eventId + "/waitlist").getBody();
        JsonNode graceBefore = grace.get("/api/events/" + eventId + "/waitlist").getBody();
        assertThat(adaBefore.get("place").asInt()).isEqualTo(1);
        assertThat(adaBefore.get("orderId").isNull()).isTrue();
        assertThat(graceBefore.get("place").asInt()).isEqualTo(2);
        assertThat(graceBefore.get("orderId").isNull()).isTrue();

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode adaLine = ada.get("/api/events/" + eventId + "/waitlist").getBody();
        JsonNode graceLine = grace.get("/api/events/" + eventId + "/waitlist").getBody();
        assertThat(adaLine.get("place").asInt()).isEqualTo(1);
        assertThat(adaLine.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(graceLine.get("place").asInt()).isEqualTo(2);
        assertThat(graceLine.get("orderId").isNull()).isTrue();
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds")).isEmpty();
        assertThat(order.get("tickets")).isEmpty();
        assertThat(order.get("payment").isNull()).isTrue();
        assertThat(order.get("holds")).hasSize(1);
        assertThat(order.get("holds").get(0).get("label").asText()).isEqualTo("A1");
        assertThat(order.get("holds").get(0).get("heldUntil").asText()).isEqualTo("2026-10-16T15:15:00Z");
        assertThat(seatState(grace.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anUnknownPaymentKeepsItsSeatUntilTheTicketIsWrittenForTheNewDate() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode during = ada.get("/api/orders/" + orderId).getBody();
        assertThat(during.get("payment").get("status").asText()).isEqualTo("unknown");
        assertThat(during.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(during)).isEmpty();
        assertThat(during.get("refunds")).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode finished = ada.get("/api/orders/" + orderId + "?lookup=success").getBody();
        assertThat(finished.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(finished.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(finished)).containsExactly("A1");
        assertThat(finished.get("refunds")).isEmpty();
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2027-03-01T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("ticket");
    }

    @Test
    void aPendingPaymentKeepsItsSeatUntilTheTicketIsWrittenForTheNewDate() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
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

            assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                    "startsAt", "2027-03-01T01:00:00Z",
                    "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode during = ada.get("/api/orders/" + orderId).getBody();
            assertThat(during.get("payment").get("status").asText()).isEqualTo("pending");
            assertThat(during.get("payment").get("amountCents").asInt()).isEqualTo(4100);
            assertThat(ticketLabels(during)).isEmpty();
            assertThat(during.get("refunds")).isEmpty();
            assertThat(seatState(grace.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
            assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            paymentGate.release();
            assertThat(checkout.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            paymentGate.release();
            pool.shutdownNow();
        }

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(order)).containsExactly("A1");
        assertThat(order.get("refunds")).isEmpty();
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2027-03-01T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("ticket");
    }

    @Test
    void aSucceededPaymentWithoutATicketKeepsItsSeatUntilTheTicketIsWrittenForTheNewDate() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        insertSucceededPayment(orderId, 4100, Instant.parse("2026-10-16T15:00:00Z"));

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:11:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode during = ada.get("/api/orders/" + orderId).getBody();
        assertThat(during.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(during.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(during)).isEmpty();
        assertThat(during.get("refunds")).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> repaired = ada.post("/api/orders/" + orderId + "/reconcile", java.util.Map.of(
                "lookup", "success"));
        assertThat(repaired.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repaired.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(repaired.getBody().get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(repaired.getBody())).containsExactly("A1");
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2027-03-01T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("ticket");
    }

    @Test
    void atExactly336HoursACustomerCancelReturnsTheFullNetPlusTheFeeShare() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-01T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        JsonNode bought = ada.get("/api/orders/" + orderId).getBody();
        assertThat(bought.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(bought.get("tickets").get(0).get("startingPriceCents").asInt()).isEqualTo(4000);
        assertThat(bought.get("tickets").get(0).get("feeShareCents").asInt()).isEqualTo(100);

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2026-11-01T15:00:01Z",
                "reason", "The venue asked once")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2026-11-01T15:00:01Z",
                "reason", "The venue asked again")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-30T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = cancelled.getBody();
        assertThat(order.get("refunds")).hasSize(1);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(order)).isEmpty();
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2026-11-01T15:00:01Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        JsonNode entries = admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).get("kind").asText()).isEqualTo("reschedule");
        assertThat(entries.get(0).get("recordedAt").asText()).isEqualTo("2026-10-01T15:00:00Z");
        assertThat(entries.get(1).get("kind").asText()).isEqualTo("reschedule");
        assertThat(entries.get(1).get("reason").asText()).isEqualTo("The venue asked again");
        assertThat(entries.get(1).get("recordedAt").asText()).isEqualTo("2026-10-16T15:00:00Z");
    }

    @Test
    void oneSecondAfter336HoursTheHalfBandKeepsTheFee() {
        int refund = cancelOneSecondAfterTheWindow("2026-11-01T15:00:01Z");
        assertThat(refund).isEqualTo(2000);
    }

    @Test
    void oneSecondAfter336HoursTheFullBandKeepsTheFee() {
        int refund = cancelOneSecondAfterTheWindow("2026-11-06T15:00:01Z");
        assertThat(refund).isEqualTo(4000);
    }

    @Test
    void oneSecondAfter336HoursTheZeroBandKeepsTheFee() {
        int refund = cancelOneSecondAfterTheWindow("2026-11-01T15:00:00Z");
        assertThat(refund).isEqualTo(0);
    }

    @Test
    void aTwentyFiveHourLocalDayDoesNotStretchThe336HourWindow() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-31T04:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2026-11-15T04:00:01Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-11-14T04:00:01Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(0);
        assertThat(ticketLabels(cancelled.getBody())).isEmpty();
    }

    @Test
    void aFeeShareAlreadyKeptOnAnEarlierCancelStaysKept() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1, a2));
        JsonNode bought = ada.get("/api/orders/" + orderId).getBody();
        assertThat(bought.get("payment").get("amountCents").asInt()).isEqualTo(8100);
        assertThat(bought.get("tickets").get(0).get("feeShareCents").asInt()).isEqualTo(50);
        assertThat(bought.get("tickets").get(1).get("feeShareCents").asInt()).isEqualTo(50);

        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("refunds").get(0).get("amountCents").asInt())
                .isEqualTo(4000);

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2026-11-01T15:00:01Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a2),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refundAmounts(cancelled.getBody())).containsExactly(4000, 4050);
        assertThat(cancelled.getBody().get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(cancelled.getBody())).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("free");
    }

    @Test
    void aRescheduleAndIts336HourRefundDoNotRestoreAPromoUse() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200);
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        long later = createEvent(admin, "2027-01-15T01:00:00Z");
        long afterRefund = createEvent(admin, "2027-02-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long adaOrder = holdAttachAndCheckout(ada, eventId, seatId(opened, "A1"), code);
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(3900);

        JsonNode laterMap = grace.get("/api/events/" + later).getBody();
        long blocked = holdAttachAndCheckout(grace, later, seatId(laterMap, "A1"), code);
        assertThat(grace.get("/api/orders/" + blocked).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(4100);

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2026-11-01T15:00:01Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode stillBlocked = grace.get("/api/events/" + later).getBody();
        long afterMove = holdAttachAndCheckout(grace, later, seatId(stillBlocked, "A2"), code);
        assertThat(grace.get("/api/orders/" + afterMove).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(4100);

        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + adaOrder + "/cancel", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A1")),
                "mock", "success"));
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(3900);
        assertThat(cancelled.getBody().get("promoCode").asText()).isEqualTo(code);

        JsonNode afterMap = grace.get("/api/events/" + afterRefund).getBody();
        long stillUsed = holdAttachAndCheckout(grace, afterRefund, seatId(afterMap, "A1"), code);
        assertThat(grace.get("/api/orders/" + stillUsed).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(4100);
    }

    @Test
    void aRefundCommittedBeforeTheRescheduleKeepsItsCents() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1, a2));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("refunds").get(0).get("amountCents").asInt())
                .isEqualTo(4000);

        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2026-11-01T15:00:01Z",
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode lookedUp = ada.get("/api/orders/" + orderId + "?refundLookup=success").getBody();
        assertThat(lookedUp.get("refunds")).hasSize(1);
        assertThat(lookedUp.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(lookedUp.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4000);
        assertThat(ticketLabels(lookedUp)).containsExactly("A2");

        ResponseEntity<JsonNode> fresh = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a2),
                "mock", "success"));
        assertThat(fresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refundAmounts(fresh.getBody())).containsExactly(4000, 4050);
    }

    private int cancelOneSecondAfterTheWindow(String startsAt) {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", startsAt,
                "reason", "The venue moved the date")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-30T15:00:01Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ticketLabels(cancelled.getBody())).isEmpty();
        assertThat(cancelled.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo(startsAt);
        return cancelled.getBody().get("refunds").get(0).get("amountCents").asInt();
    }

    private long holdAttachAndCheckout(ApiSession customer, long eventId, long seatId, String code) {
        ResponseEntity<JsonNode> held = customer.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(customer.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of("code", code))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(customer.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(seatId),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        return orderId;
    }

    private String createPromo(ApiSession admin, int amountCents) {
        String code = "MOVE-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "amountCents", amountCents,
                "globalLimit", 1,
                "perCustomerLimit", 1,
                "exclusive", false));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    private static List<Integer> refundAmounts(JsonNode order) {
        List<Integer> amounts = new java.util.ArrayList<>();
        for (JsonNode refund : order.get("refunds")) {
            amounts.add(refund.get("amountCents").asInt());
        }
        return amounts;
    }

    private void insertSucceededPayment(long orderId, int amountCents, Instant createdAt) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into payment (order_id, amount_cents, status, created_at) values (?, ?, 'SUCCEEDED', ?)")) {
            statement.setLong(1, orderId);
            statement.setInt(2, amountCents);
            statement.setTimestamp(3, Timestamp.from(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void blockExcept(ApiSession admin, long eventId, JsonNode map, String kept) {
        for (JsonNode section : map.get("sections")) {
            for (JsonNode seat : section.get("seats")) {
                if (kept.equals(seat.get("label").asText())) {
                    continue;
                }
                assertThat(admin.post(
                        "/api/admin/events/" + eventId + "/seats/" + seat.get("id").asLong() + "/block",
                        java.util.Map.of("reason", "Hold the house")).getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }

    private long holdAndCheckout(ApiSession customer, long eventId, List<Long> seatIds) {
        ResponseEntity<JsonNode> held = customer.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", seatIds));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        ResponseEntity<JsonNode> checkedOut = customer.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seatIds,
                "mock", "success"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        return orderId;
    }

    private static List<String> ticketLabels(JsonNode order) {
        List<String> labels = new java.util.ArrayList<>();
        for (JsonNode ticket : order.get("tickets")) {
            labels.add(ticket.get("label").asText());
        }
        return labels;
    }

    private static long seatId(JsonNode event, String label) {
        return seat(event, label).get("id").asLong();
    }

    private long createEvent(ApiSession admin, String startsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "Reschedule-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", startsAt,
                "timeZone", "America/New_York",
                "earlyBirdStartsAt", "2026-10-01T00:00:00Z",
                "earlyBirdEndsAt", "2026-11-15T00:00:00Z"));
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

    private static String seatState(JsonNode event, String label) {
        return seat(event, label).get("state").asText();
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
