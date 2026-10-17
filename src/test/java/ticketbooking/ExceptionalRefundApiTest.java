package ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;

import ticketbooking.checkout.PausingRefundAttemptGate;
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
class ExceptionalRefundApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PausingRefundAttemptGate refundGate;

    @Test
    void anAdminNamesTheCentsAndTheTicketsStay() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1, a2));

        ResponseEntity<JsonNode> issued = admin.post(
                "/api/admin/orders/" + orderId + "/exceptional-refunds",
                java.util.Map.of(
                        "amountCents", 42,
                        "reason", "Goodwill for the rain delay",
                        "mock", "success"));

        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(order.get("refunds")).hasSize(1);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(ticketLabels(order)).containsExactlyInAnyOrder("A1", "A2");
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(map, "A1")).isEqualTo("ticket");
        assertThat(seatState(map, "A2")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<JsonNode> entries = admin.get("/api/admin/events/" + eventId + "/audit-entries");
        assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entries.getBody()).hasSize(1);
        assertThat(entries.getBody().get(0).get("kind").asText()).isEqualTo("exceptional-refund");
        assertThat(entries.getBody().get(0).get("reason").asText()).isEqualTo("Goodwill for the rain delay");
        assertThat(entries.getBody().get(0).get("seatId").isNull()).isTrue();
    }

    @Test
    void aCustomerIsRefusedAnExceptionalRefund() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        ResponseEntity<JsonNode> refused = ada.post(
                "/api/admin/orders/" + orderId + "/exceptional-refunds",
                java.util.Map.of("amountCents", 42, "reason", "I want my money", "mock", "success"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds")).isEmpty();
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(order)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
    }

    @Test
    void anExceptionalRefundWithoutAReasonIsRefused() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        String path = "/api/admin/orders/" + orderId + "/exceptional-refunds";

        assertThat(admin.post(path, java.util.Map.of("amountCents", 42, "reason", "", "mock", "success"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("amountCents", 42, "reason", "   ", "mock", "success"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("amountCents", 42, "mock", "success"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds")).isEmpty();
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(order)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
    }

    @Test
    void thePendingExceptionalRefundIsVisibleBeforeTheMockReturns() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        ApiSession again = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        refundGate.arm();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<JsonNode>> issued = pool.submit(() -> admin.post(
                    "/api/admin/orders/" + orderId + "/exceptional-refunds",
                    java.util.Map.of("amountCents", 42, "reason", "Rain delay", "mock", "success")));
            assertThat(refundGate.awaitArrival(Duration.ofSeconds(10))).isTrue();
            JsonNode during = ada.get("/api/orders/" + orderId).getBody();
            assertThat(during.get("refunds")).hasSize(1);
            assertThat(during.get("refunds").get(0).get("status").asText()).isEqualTo("pending");
            assertThat(during.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
            assertThat(ticketLabels(during)).containsExactly("A1");
            assertThat(during.get("payment").get("amountCents").asInt()).isEqualTo(5100);
            assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
            assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ResponseEntity<JsonNode> second = again.post(
                    "/api/admin/orders/" + orderId + "/exceptional-refunds",
                    java.util.Map.of("amountCents", 99, "reason", "Another try", "mock", "success"));
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(ada.get("/api/orders/" + orderId).getBody().get("refunds")).hasSize(1);
            refundGate.release();
            assertThat(issued.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            refundGate.release();
            pool.shutdownNow();
        }

        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds")).hasSize(1);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(ticketLabels(order)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
    }

    @Test
    void aDeclinedExceptionalRefundLeavesTheMoneyAndTheNextStoresTheNamedCents() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        ResponseEntity<JsonNode> declined = admin.post(
                "/api/admin/orders/" + orderId + "/exceptional-refunds",
                java.util.Map.of("amountCents", 42, "reason", "Rain delay", "mock", "decline"));

        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(declined.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(declined.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        JsonNode afterDecline = ada.get("/api/orders/" + orderId).getBody();
        assertThat(afterDecline.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(afterDecline.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(afterDecline.get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(ticketLabels(afterDecline)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> repaired = admin.post(
                "/api/admin/orders/" + orderId + "/exceptional-refunds",
                java.util.Map.of("amountCents", 777, "reason", "Still the rain", "mock", "success"));

        assertThat(repaired.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = ada.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("refunds")).hasSize(2);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("failed");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(order.get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(1).get("amountCents").asInt()).isEqualTo(777);
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(order)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
    }

    @Test
    void timeoutAndUnknownLeaveTheExceptionalRefundUnknownAndTheSeatATicket() {
        unfinishedExceptionalRefund("timeout");
        unfinishedExceptionalRefund("unknown");
    }

    @Test
    void anUnknownExceptionalRefundFinishesOnlyByLookupAndKeepsTheTickets() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));
        assertThat(admin.post("/api/admin/orders/" + orderId + "/exceptional-refunds", java.util.Map.of(
                "amountCents", 42,
                "reason", "Rain delay",
                "mock", "timeout")).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode untouched = ada.get("/api/orders/" + orderId).getBody();
        assertThat(untouched.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(untouched.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(ticketLabels(untouched)).containsExactly("A1");

        JsonNode stillUnknown = ada.get("/api/orders/" + orderId + "?refundLookup=unknown").getBody();
        assertThat(stillUnknown.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(stillUnknown.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(ticketLabels(stillUnknown)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");

        ResponseEntity<JsonNode> lookedUp = ada.get("/api/orders/" + orderId + "?refundLookup=success");
        assertThat(lookedUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookedUp.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(lookedUp.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(lookedUp.getBody().get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(lookedUp.getBody())).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode again = ada.get("/api/orders/" + orderId + "?refundLookup=decline").getBody();
        assertThat(again.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(again.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(ticketLabels(again)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
    }

    private void unfinishedExceptionalRefund(String mock) {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        ResponseEntity<JsonNode> issued = admin.post(
                "/api/admin/orders/" + orderId + "/exceptional-refunds",
                java.util.Map.of("amountCents", 42, "reason", "Rain delay", "mock", mock));

        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(issued.getBody().get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(issued.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(ticketLabels(issued.getBody())).containsExactly("A1");
        assertThat(issued.getBody().get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
        assertThat(grace.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", List.of(a1)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<JsonNode> second = admin.post(
                "/api/admin/orders/" + orderId + "/exceptional-refunds",
                java.util.Map.of("amountCents", 42, "reason", "Again", "mock", "success"));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode still = ada.get("/api/orders/" + orderId).getBody();
        assertThat(still.get("refunds")).hasSize(1);
        assertThat(still.get("refunds").get(0).get("status").asText()).isEqualTo("unknown");
        assertThat(ticketLabels(still)).containsExactly("A1");
    }

    @Test
    void anExceptionalRefundDoesNotRestoreThePromoUse() {
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
        assertThat(ada.get("/api/orders/" + adaOrder).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(4900);

        assertThat(admin.post("/api/admin/orders/" + adaOrder + "/exceptional-refunds", java.util.Map.of(
                "amountCents", 42,
                "reason", "Goodwill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode adaAfter = ada.get("/api/orders/" + adaOrder).getBody();
        assertThat(adaAfter.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(42);
        assertThat(adaAfter.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(ticketLabels(adaAfter)).containsExactly("A1");
        assertThat(adaAfter.get("promoCode").asText()).isEqualTo(code);

        ResponseEntity<JsonNode> graceHeld = grace.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a2)));
        assertThat(graceHeld.getStatusCode()).isEqualTo(HttpStatus.OK);
        long graceOrder = graceHeld.getBody().get("orderId").asLong();
        assertThat(grace.post("/api/orders/" + graceOrder + "/promo-code", java.util.Map.of("code", code))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(grace.post("/api/orders/" + graceOrder + "/checkout", java.util.Map.of(
                "seatIds", List.of(a2),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(5100);
        assertThat(grace.get("/api/orders/" + graceOrder).getBody().get("payment").get("status").asText())
                .isEqualTo("succeeded");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");
    }

    @Test
    void theNamedCentsCanIncludeTheFeeAndALaterCancelStillUsesTheBand() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-14T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long orderId = holdAndCheckout(ada, eventId, List.of(a1));

        ResponseEntity<JsonNode> issued = admin.post(
                "/api/admin/orders/" + orderId + "/exceptional-refunds",
                java.util.Map.of("amountCents", 5100, "reason", "Return the fee too", "mock", "success"));

        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode afterExceptional = ada.get("/api/orders/" + orderId).getBody();
        assertThat(afterExceptional.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(afterExceptional.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5100);
        assertThat(afterExceptional.get("payment").get("amountCents").asInt()).isEqualTo(5100);
        assertThat(ticketLabels(afterExceptional)).containsExactly("A1");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("ticket");

        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("refunds")).hasSize(2);
        assertThat(cancelled.getBody().get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5100);
        assertThat(cancelled.getBody().get("refunds").get(1).get("status").asText()).isEqualTo("succeeded");
        assertThat(cancelled.getBody().get("refunds").get(1).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(cancelled.getBody())).isEmpty();
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
    }

    private String createPromo(ApiSession admin, int amountCents) {
        String code = "EXCEPT-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "amountCents", amountCents,
                "globalLimit", 1,
                "perCustomerLimit", 1,
                "exclusive", false));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
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

    private long createEvent(ApiSession admin, String startsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "ExceptionalNight-" + UUID.randomUUID();
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
        List<String> labels = new ArrayList<>();
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
