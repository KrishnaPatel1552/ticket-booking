package com.copart.prep;

import static org.assertj.core.api.Assertions.assertThat;

import com.copart.prep.checkout.PausingRefundAttemptGate;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
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
class EventCancellationApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PausingRefundAttemptGate refundGate;

    @Test
    void anAdminCancelsTheEventWithAReasonAndTakesEverySeatOffSale() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        assertThat(seatState(opened, "A1")).isEqualTo("free");

        ResponseEntity<JsonNode> cancelled = admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(map, "A1")).isEqualTo("blocked");
        assertThat(seatState(map, "B8")).isEqualTo("blocked");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode sales = admin.get("/api/admin/events/" + eventId + "/sales").getBody();
        assertThat(sales.get("free").asInt()).isEqualTo(0);
        assertThat(sales.get("held").asInt()).isEqualTo(0);
        assertThat(sales.get("blocked").asInt()).isEqualTo(0);
        assertThat(sales.get("tickets").asInt()).isEqualTo(0);
        assertThat(sales.get("ticketsSold").asInt()).isEqualTo(0);
        ResponseEntity<JsonNode> entries = admin.get("/api/admin/events/" + eventId + "/audit-entries");
        assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entries.getBody()).hasSize(1);
        assertThat(entries.getBody().get(0).get("kind").asText()).isEqualTo("event-cancellation");
        assertThat(entries.getBody().get(0).get("reason").asText()).isEqualTo("The artist is ill");
        assertThat(entries.getBody().get(0).get("seatId").isNull()).isTrue();
        assertThat(entries.getBody().get(0).get("recordedAt").asText()).isEqualTo("2026-10-16T15:00:00Z");
    }

    @Test
    void aCustomerIsRefusedAnEventCancellation() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");

        ResponseEntity<JsonNode> refused = ada.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "I cannot attend",
                "mock", "success"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
        assertThat(ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
        JsonNode sales = admin.get("/api/admin/events/" + eventId + "/sales").getBody();
        assertThat(sales.get("free").asInt()).isEqualTo(19);
        assertThat(sales.get("held").asInt()).isEqualTo(1);
    }

    @Test
    void anEventCancellationWithoutAReasonIsRefused() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String path = "/api/admin/events/" + eventId + "/cancellation";

        assertThat(admin.post(path, java.util.Map.of("reason", "", "mock", "success")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("reason", "   ", "mock", "success")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("mock", "success")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
        assertThat(admin.get("/api/admin/events/" + eventId + "/sales").getBody().get("free").asInt()).isEqualTo(20);
    }

    @Test
    void aRemainingTicketIsRefundedForItsNetPlusFeeAndStaysOffSale() {
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

        ResponseEntity<JsonNode> cancelled = admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(order.get("refunds")).hasSize(1);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4100);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode sales = admin.get("/api/admin/events/" + eventId + "/sales").getBody();
        assertThat(sales.get("free").asInt()).isEqualTo(0);
        assertThat(sales.get("tickets").asInt()).isEqualTo(0);
        assertThat(sales.get("ticketsSold").asInt()).isEqualTo(1);
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).hasSize(1);
    }

    @Test
    void aSucceededCustomerCancelKeepsTheFeeAndTheEventCancellationReturnsOnlyThatShare() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        ResponseEntity<JsonNode> customerCancel = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(customerCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(customerCancel.getBody().get("refunds")).hasSize(1);
        assertThat(customerCancel.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(customerCancel.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4000);
        assertThat(ticketLabels(customerCancel.getBody())).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(refundAmounts(order)).containsExactly(4000, 100);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode sales = admin.get("/api/admin/events/" + eventId + "/sales").getBody();
        assertThat(sales.get("free").asInt()).isEqualTo(0);
        assertThat(sales.get("tickets").asInt()).isEqualTo(0);
        assertThat(sales.get("ticketsSold").asInt()).isEqualTo(1);
    }

    @Test
    void aHalfBandCustomerCancelStillReturnsOnlyTheKeptFee() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-20T15:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("refunds").get(0).get("amountCents").asInt())
                .isEqualTo(2000);

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(refundAmounts(order)).containsExactly(2000, 100);
        assertThat(order.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
    }

    @Test
    void aZeroBandCustomerCancelStillReturnsOnlyTheKeptFee() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-17T15:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("refunds").get(0).get("amountCents").asInt())
                .isEqualTo(0);

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(refundAmounts(order)).containsExactly(0, 100);
        assertThat(order.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(order)).isEmpty();
    }

    @Test
    void eachRemainingTicketGetsItsOwnNetPlusFeeRefund() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long b1 = seatId(opened, "B1");
        long adaOrder = holdAndCheckout(ada, eventId, List.of(a1, a2));
        long graceOrder = holdAndCheckout(grace, eventId, List.of(b1));

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode adaAfter = ada.get("/api/orders/" + adaOrder).getBody();
        assertThat(adaAfter.get("payment").get("amountCents").asInt()).isEqualTo(8100);
        assertThat(ticketLabels(adaAfter)).isEmpty();
        assertThat(refundAmounts(adaAfter)).containsExactly(4050, 4050);
        assertThat(adaAfter.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(adaAfter.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        JsonNode graceAfter = grace.get("/api/orders/" + graceOrder).getBody();
        assertThat(graceAfter.get("payment").get("amountCents").asInt()).isEqualTo(4100);
        assertThat(refundAmounts(graceAfter)).containsExactly(4100);
        assertThat(ticketLabels(graceAfter)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "B1")).isEqualTo("blocked");
        JsonNode sales = admin.get("/api/admin/events/" + eventId + "/sales").getBody();
        assertThat(sales.get("free").asInt()).isEqualTo(0);
        assertThat(sales.get("tickets").asInt()).isEqualTo(0);
        assertThat(sales.get("ticketsSold").asInt()).isEqualTo(3);
    }

    @Test
    void aSuccessfulLookupReturnsOnlyTheKeptFeeAndDoesNotPayTheNetAgain() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("refunds").get(0).get("status").asText())
                .isEqualTo("unknown");

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "decline",
                "lookup", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4000);
        assertThat(order.get("refunds").get(1).get("status").asText()).isEqualTo("failed");
        assertThat(order.get("refunds").get(1).get("amountCents").asInt()).isEqualTo(100);
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).hasSize(1);
    }

    @Test
    void aDeclinedLookupRefundsTheFullNetPlusTheFee() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "unknown")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success",
                "lookup", "decline")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4000);
        assertThat(order.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(1).get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        JsonNode sales = admin.get("/api/admin/events/" + eventId + "/sales").getBody();
        assertThat(sales.get("tickets").asInt()).isEqualTo(0);
        assertThat(sales.get("ticketsSold").asInt()).isEqualTo(1);
        assertThat(sales.get("free").asInt()).isEqualTo(0);
    }

    @Test
    void anUnknownLookupLeavesTheCustomerRefundAndSkipsTheEventCancellationRefund() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success",
                "lookup", "unknown")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode during = ada.get("/api/orders/" + orderId).getBody();
        assertThat(during.get("refunds")).hasSize(1);
        assertThat(during.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(during.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4000);
        assertThat(ticketLabels(during)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(admin.get("/api/admin/events/" + eventId + "/sales").getBody().get("tickets").asInt()).isEqualTo(1);

        JsonNode lookedUp = ada.get("/api/orders/" + orderId + "?refundLookup=success").getBody();
        assertThat(lookedUp.get("refunds")).hasSize(1);
        assertThat(lookedUp.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(lookedUp.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4000);
        assertThat(ticketLabels(lookedUp)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is still ill",
                "mock", "success",
                "lookup", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode after = ada.get("/api/orders/" + orderId).getBody();
        assertThat(refundAmounts(after)).containsExactly(4000, 100);
        assertThat(after.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).hasSize(1);
    }

    @Test
    void aFailedEventCancellationRefundIsIssuedAgainForTheSameCents() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "decline")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode declined = ada.get("/api/orders/" + orderId).getBody();
        assertThat(declined.get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(declined.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(declined)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "Try the refund again",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode repaired = ada.get("/api/orders/" + orderId).getBody();
        assertThat(refundAmounts(repaired)).containsExactly(4100, 4100);
        assertThat(repaired.get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(repaired.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(repaired)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).hasSize(1);
        assertThat(admin.get("/api/admin/events/" + eventId + "/sales").getBody().get("ticketsSold").asInt())
                .isEqualTo(1);
    }

    @Test
    void timeoutAndUnknownStoreUnknownUntilALaterLookup() {
        unfinishedEventRefund("timeout");
        unfinishedEventRefund("unknown");
    }

    private void unfinishedEventRefund(String mock) {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", mock)).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode unknown = ada.get("/api/orders/" + orderId).getBody();
        assertThat(unknown.get("refunds")).hasSize(1);
        assertThat(unknown.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(unknown.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(unknown)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "Again",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode still = ada.get("/api/orders/" + orderId).getBody();
        assertThat(still.get("refunds")).hasSize(1);
        assertThat(still.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(ticketLabels(still)).containsExactly("A1");

        JsonNode lookedUp = ada.get("/api/orders/" + orderId + "?refundLookup=success").getBody();
        assertThat(lookedUp.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(lookedUp.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(lookedUp)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        assertThat(admin.get("/api/admin/events/" + eventId + "/sales").getBody().get("free").asInt()).isEqualTo(0);
    }

    @Test
    void thePendingEventCancellationRefundIsVisibleBeforeTheMockReturns() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        ApiSession again = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        refundGate.arm();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<JsonNode>> cancelled = pool.submit(() -> admin.post(
                    "/api/admin/events/" + eventId + "/cancellation",
                    java.util.Map.of("reason", "The artist is ill", "mock", "success")));
            assertThat(refundGate.awaitArrival(Duration.ofSeconds(10))).isTrue();
            JsonNode during = ada.get("/api/orders/" + orderId).getBody();
            assertThat(during.get("refunds")).hasSize(1);
            assertThat(during.get("refunds").get(0).get("status").asText()).isEqualTo("pending");
            assertThat(during.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4100);
            assertThat(ticketLabels(during)).containsExactly("A1");
            assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
            assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(admin.get("/api/admin/events/" + eventId + "/sales").getBody().get("free").asInt())
                    .isEqualTo(0);
            assertThat(admin.get("/api/admin/events/" + eventId + "/sales").getBody().get("tickets").asInt())
                    .isEqualTo(1);
            assertThat(again.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                    "reason", "Again",
                    "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(ada.get("/api/orders/" + orderId).getBody().get("refunds")).hasSize(1);
            refundGate.release();
            assertThat(cancelled.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            refundGate.release();
            pool.shutdownNow();
        }

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds")).hasSize(1);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(4100);
        assertThat(ticketLabels(order)).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
    }

    @Test
    void recordingTheCancellationRestoresPromoUsesEvenWhenTheRefundFails() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 200);
        long cancelledEvent = createEvent(admin, "2026-12-15T01:00:00Z");
        long laterEvent = createEvent(admin, "2027-01-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode firstMap = ada.get("/api/events/" + cancelledEvent).getBody();
        long adaOrder = holdAttachAndCheckout(ada, cancelledEvent, seatId(firstMap, "A1"), code);
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(3900);

        JsonNode sameMap = grace.get("/api/events/" + cancelledEvent).getBody();
        long graceBlocked = holdAttachAndCheckout(grace, cancelledEvent, seatId(sameMap, "A2"), code);
        assertThat(grace.get("/api/orders/" + graceBlocked).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(4100);

        assertThat(admin.post("/api/admin/events/" + cancelledEvent + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "decline")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode adaAfter = ada.get("/api/orders/" + adaOrder).getBody();
        assertThat(adaAfter.get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(adaAfter.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(3900);
        assertThat(ticketLabels(adaAfter)).containsExactly("A1");
        assertThat(adaAfter.get("promoCode").asText()).isEqualTo(code);

        JsonNode laterMap = grace.get("/api/events/" + laterEvent).getBody();
        long restored = holdAttachAndCheckout(grace, laterEvent, seatId(laterMap, "A1"), code);
        assertThat(grace.get("/api/orders/" + restored).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(3900);
        assertThat(grace.get("/api/orders/" + restored).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
    }

    @Test
    void cancellingTheEventEndsTheWaitlistAndDoesNotOfferTheFreedSeat() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2027-06-01T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long adaFirst = sell(ada, eventId, opened, List.of("A1", "A2", "A3", "A4", "A5", "A6"));
        sell(ada, eventId, opened, List.of("A7", "A8", "A9", "A10", "A11", "A12"));
        sell(grace, eventId, opened, List.of("B1", "B2", "B3", "B4", "B5", "B6"));
        sell(grace, eventId, opened, List.of("B7", "B8"));
        assertThat(ada.post("/api/events/" + eventId + "/waitlist", java.util.Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(grace.post("/api/events/" + eventId + "/waitlist", java.util.Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(ada.post("/api/orders/" + adaFirst + "/cancel", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A1")),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode adaLine = ada.get("/api/events/" + eventId + "/waitlist").getBody();
        assertThat(adaLine.get("place").asInt()).isEqualTo(1);
        long offerOrderId = adaLine.get("orderId").asLong();
        assertThat(seatState(grace.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(grace.get("/api/events/" + eventId + "/waitlist").getBody().get("place").asInt()).isEqualTo(2);

        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(ada.get("/api/events/" + eventId + "/waitlist").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(grace.get("/api/events/" + eventId + "/waitlist").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ada.post("/api/events/" + eventId + "/waitlist", java.util.Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        JsonNode offer = ada.get("/api/orders/" + offerOrderId).getBody();
        assertThat(offer.get("holds")).isEmpty();
        assertThat(offer.get("tickets")).isEmpty();
        assertThat(seatState(grace.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        assertThat(seatState(grace.get("/api/events/" + eventId).getBody(), "B8")).isEqualTo("blocked");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A1")))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode sales = admin.get("/api/admin/events/" + eventId + "/sales").getBody();
        assertThat(sales.get("free").asInt()).isEqualTo(0);
        assertThat(sales.get("held").asInt()).isEqualTo(0);
        assertThat(sales.get("tickets").asInt()).isEqualTo(0);
        assertThat(sales.get("ticketsSold").asInt()).isEqualTo(20);
        JsonNode adaOrder = ada.get("/api/orders/" + adaFirst).getBody();
        assertThat(ticketLabels(adaOrder)).isEmpty();
        assertThat(refundAmounts(adaOrder)).contains(3915, 20);
    }

    private long sell(ApiSession customer, long eventId, JsonNode map, List<String> labels) {
        List<Long> seatIds = new ArrayList<>();
        for (String label : labels) {
            seatIds.add(seatId(map, label));
        }
        return holdAndCheckout(customer, eventId, seatIds);
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

    private String createPromo(ApiSession admin, int discountCents) {
        String code = "EVENT-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "discountCents", discountCents,
                "globalLimit", 1,
                "perCustomerLimit", 1,
                "exclusive", false));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    private static List<Integer> refundAmounts(JsonNode order) {
        List<Integer> amounts = new ArrayList<>();
        for (JsonNode refund : order.get("refunds")) {
            amounts.add(refund.get("amountCents").asInt());
        }
        return amounts;
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

    private long createEvent(ApiSession admin, String startsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "CancelEvent-" + UUID.randomUUID();
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
