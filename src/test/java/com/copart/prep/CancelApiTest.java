package com.copart.prep;

import static org.assertj.core.api.Assertions.assertThat;

import com.copart.prep.checkout.PausingRefundAttemptGate;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
class CancelApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PausingRefundAttemptGate refundGate;

    @Test
    void atExactlyOneHundredSixtyEightHoursAPartialCancelRefundsThatTicketsFullNet() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1, a2)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = cancelled.getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(order.get("refunds")).hasSize(1);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(order)).containsExactly("A2");
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("free");
        assertThat(seatState(after, "A2")).isEqualTo("ticket");
        JsonNode again = ada.get("/api/orders/" + orderId).getBody();
        assertThat(again.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(again.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(again)).containsExactly("A2");
    }

    @Test
    void cancellingEveryTicketRefundsTheNetsAndKeepsTheFee() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1, a2));

        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(cancelled.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(cancelled.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(cancelled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(10000);
        assertThat(ticketLabels(cancelled.getBody())).isEmpty();
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("free");
        assertThat(seatState(after, "A2")).isEqualTo("free");
        ResponseEntity<JsonNode> heldAgain = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(heldAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        JsonNode lookedUp = ada.get("/api/orders/" + orderId + "?lookup=decline").getBody();
        assertThat(lookedUp.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(lookedUp.get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(ticketLabels(lookedUp)).isEmpty();
        assertThat(lookedUp.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(10000);
    }

    @Test
    void aDeclinedRefundLeavesTheTicketsAndTheSeat() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "decline"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(cancelled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(cancelled.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(cancelled.getBody().get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(cancelled.getBody())).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void timeoutAndUnknownLeaveTheRefundUnknownAndTheSeatTaken() {
        unfinishedRefundKeepsTheSeat("timeout");
        unfinishedRefundKeepsTheSeat("unknown");
    }

    private void unfinishedRefundKeepsTheSeat(String mock) {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", mock));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(cancelled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(cancelled.getBody())).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<JsonNode> again = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode still = ada.get("/api/orders/" + orderId).getBody();
        assertThat(still.get("refunds")).hasSize(1);
        assertThat(still.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(ticketLabels(still)).containsExactly("A1");
    }

    @Test
    void thePendingRefundIsVisibleBeforeTheMockReturns() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession watcher = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        refundGate.arm();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<JsonNode>> cancel = pool.submit(() -> ada.post(
                    "/api/orders/" + orderId + "/cancel",
                    java.util.Map.of("seatIds", List.of(a1), "mock", "success")));
            assertThat(refundGate.awaitArrival(Duration.ofSeconds(10))).isTrue();
            JsonNode during = watcher.get("/api/orders/" + orderId).getBody();
            assertThat(during.get("refunds")).hasSize(1);
            assertThat(during.get("refunds").get(0).get("status").asText()).isEqualTo("pending");
            assertThat(during.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
            assertThat(ticketLabels(during)).containsExactly("A1");
            assertThat(seatState(watcher.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
            assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ResponseEntity<JsonNode> second = watcher.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                    "seatIds", List.of(a1),
                    "mock", "success"));
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(watcher.get("/api/orders/" + orderId).getBody().get("refunds")).hasSize(1);
            refundGate.release();
            assertThat(cancel.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            refundGate.release();
            pool.shutdownNow();
        }

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
    }

    @Test
    void openingTheOrderLooksUpAnUnknownRefundOnceAndTakesTheTicketOff() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "unknown")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> lookedUp = ada.get("/api/orders/" + orderId + "?refundLookup=success");

        assertThat(lookedUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookedUp.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(lookedUp.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(lookedUp.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(lookedUp.getBody())).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
        JsonNode again = ada.get("/api/orders/" + orderId + "?refundLookup=decline").getBody();
        assertThat(again.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(again)).isEmpty();
    }

    @Test
    void aDeclinedRefundLookupFailsItAndAnUnknownLookupLeavesIt() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1, a2));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode stillUnknown = ada.get("/api/orders/" + orderId + "?refundLookup=unknown").getBody();
        assertThat(stillUnknown.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(stillUnknown.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(10000);
        assertThat(ticketLabels(stillUnknown)).containsExactlyInAnyOrder("A1", "A2");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");

        JsonNode failed = ada.get("/api/orders/" + orderId + "?refundLookup=decline").getBody();
        assertThat(failed.get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(failed.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(10000);
        assertThat(ticketLabels(failed)).containsExactlyInAnyOrder("A1", "A2");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reconcileLooksUpAnUnknownRefundAndTakesTheTicketsOff() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> reconciled = ada.post("/api/orders/" + orderId + "/refunds/reconcile", java.util.Map.of(
                "lookup", "success"));

        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reconciled.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(reconciled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(reconciled.getBody().get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(reconciled.getBody())).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
    }

    @Test
    void aPaymentLookupDoesNotFinishTheRefund() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode paymentLookup = ada.get("/api/orders/" + orderId + "?lookup=decline").getBody();
        assertThat(paymentLookup.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(paymentLookup.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(paymentLookup.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(ticketLabels(paymentLookup)).containsExactly("A1");

        JsonNode refundLookup = ada.get("/api/orders/" + orderId + "?refundLookup=success").getBody();
        assertThat(refundLookup.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(refundLookup.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(refundLookup)).isEmpty();
    }

    @Test
    void aSeatThatIsNotTheCustomersTicketWritesNoRefund() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long adaOrder = holdAndCheckout(ada, eventId, List.of(a1));
        long graceOrder = holdAndCheckout(grace, eventId, List.of(a2));

        ResponseEntity<JsonNode> mixed = ada.post("/api/orders/" + adaOrder + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "success"));
        assertThat(mixed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("refunds")).isEmpty();
        assertThat(ticketLabels(ada.get("/api/orders/" + adaOrder).getBody())).containsExactly("A1");
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("refunds")).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("ticket");

        ResponseEntity<JsonNode> freeSeat = ada.post("/api/orders/" + adaOrder + "/cancel", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A3")),
                "mock", "success"));
        assertThat(freeSeat.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("refunds")).isEmpty();

        ResponseEntity<JsonNode> otherOrder = grace.post("/api/orders/" + adaOrder + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(otherOrder.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("refunds")).isEmpty();
        assertThat(ticketLabels(ada.get("/api/orders/" + adaOrder).getBody())).containsExactly("A1");
    }

    @Test
    void afterOneHundredSixtyEightHoursTheRefundIsHalfThroughExactlyFortyEightHours() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long a3 = seatId(opened, "A3");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1, a2, a3));

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:01Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> justAfter = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(justAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(justAfter.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(2500);
        assertThat(ticketLabels(justAfter.getBody())).containsExactlyInAnyOrder("A2", "A3");

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-13T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> atFortyEight = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a2),
                "mock", "success"));
        assertThat(atFortyEight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atFortyEight.getBody().get("refunds").get(1).get("amountCents").asInt()).isEqualTo(2500);

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-13T01:00:01Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> underFortyEight = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a3),
                "mock", "success"));
        assertThat(underFortyEight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(underFortyEight.getBody().get("refunds").get(2).get("status").asText()).isEqualTo("succeeded");
        assertThat(underFortyEight.getBody().get("refunds").get(2).get("amountCents").asInt()).isEqualTo(0);
        assertThat(ticketLabels(underFortyEight.getBody())).isEmpty();
        assertThat(underFortyEight.getBody().get("payment").get("amountCents").asInt()).isEqualTo(15100);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A3")).isEqualTo("free");
    }

    @Test
    void aCancelAcrossTheFallBackSundayStillUsesOneHundredSixtyEightElapsedHours() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-11-08T05:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-11-01T05:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1, a2));

        ResponseEntity<JsonNode> atBoundary = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(atBoundary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atBoundary.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(atBoundary.getBody())).containsExactly("A2");

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-11-01T05:00:01Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> oneSecondLater = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a2),
                "mock", "success"));
        assertThat(oneSecondLater.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(oneSecondLater.getBody().get("refunds").get(1).get("amountCents").asInt()).isEqualTo(2500);
        assertThat(ticketLabels(oneSecondLater.getBody())).isEmpty();
    }

    @Test
    void halfOfAnOddNetRoundsUpToFiveHundredOneCents() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 3999);
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-13T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode ticket = ada.get("/api/orders/" + orderId).getBody().get("tickets").get(0);
        assertThat(ticket.get("startingPriceCents").asInt()).isEqualTo(5000);
        assertThat(ticket.get("groupShareCents").asInt()).isEqualTo(0);
        assertThat(ticket.get("promoShareCents").asInt()).isEqualTo(3999);
        assertThat(ticket.get("feeShareCents").asInt()).isEqualTo(100);

        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("payment").get("amountCents").asInt()).isEqualTo(1101);
        assertThat(cancelled.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(cancelled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(501);
        assertThat(ticketLabels(cancelled.getBody())).isEmpty();
    }

    @Test
    void aFailedRefundIsRepairedForTheSameCentsInAWorseBand() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        ResponseEntity<JsonNode> failed = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "decline"));
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(failed.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(failed.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(failed.getBody())).containsExactly("A1");

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> repaired = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(repaired.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repaired.getBody().get("refunds")).hasSize(2);
        assertThat(repaired.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(repaired.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(repaired.getBody().get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(repaired.getBody().get("refunds").get(1).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(repaired.getBody())).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
    }

    @Test
    void aCancelDoesNotRestoreThePromoUse() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200);
        long eventId = createEvent(admin, "2027-06-01T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-01-01T00:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long adaOrder = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + adaOrder + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(ada.post("/api/orders/" + adaOrder + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("amountCents").asInt()).isEqualTo(4900);
        assertThat(ada.post("/api/orders/" + adaOrder + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> graceHeld = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a2)));
        assertThat(graceHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        long graceOrder = graceHeld.getBody().get("orderId").asLong();
        assertThat(grace.post("/api/orders/" + graceOrder + "/promo-code", java.util.Map.of("code", code)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(grace.post("/api/orders/" + graceOrder + "/checkout", java.util.Map.of(
                "seatIds", List.of(a2),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(5100);
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
    }

    @Test
    void anUnknownRefundKeepsTheCapAndASucceededOneFreesTheSeatForTheSameCustomer() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2027-08-01T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-01-01T00:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> firstSix = List.of(
                seatId(opened, "A1"),
                seatId(opened, "A2"),
                seatId(opened, "A3"),
                seatId(opened, "A4"),
                seatId(opened, "A5"),
                seatId(opened, "A6"));
        List<Long> secondSix = List.of(
                seatId(opened, "A7"),
                seatId(opened, "A8"),
                seatId(opened, "A9"),
                seatId(opened, "A10"),
                seatId(opened, "A11"),
                seatId(opened, "A12"));
        long firstOrder = holdAndCheckout(ada, eventId, firstSix);
        holdAndCheckout(ada, eventId, secondSix);
        long b1 = seatId(opened, "B1");
        long a1 = seatId(opened, "A1");
        assertThat(ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(b1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ada.post("/api/orders/" + firstOrder + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + firstOrder).getBody().get("refunds").get(0).get("status").asText())
                .isEqualTo("unknown");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(b1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> lookedUp = ada.get("/api/orders/" + firstOrder + "?refundLookup=success");
        assertThat(lookedUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookedUp.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
        ResponseEntity<JsonNode> heldAgain = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(heldAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(b1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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

    private String createPromo(ApiSession admin, int amountCents) {
        String code = "CANCEL-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "amountCents", amountCents,
                "globalLimit", 1,
                "perCustomerLimit", 1,
                "exclusive", false));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    private long createEvent(ApiSession admin, String startsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "CancelNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", startsAt,
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
        return seat(event, label).get("id").asLong();
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
