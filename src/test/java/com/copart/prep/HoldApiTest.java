package com.copart.prep;

import static org.assertj.core.api.Assertions.assertThat;

import com.copart.prep.hold.PausingHoldGate;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import java.util.stream.IntStream;
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
class HoldApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PausingHoldGate pausingHoldGate;

    @Test
    void customerHoldsFreeSeatsAndTheSeatMapShowsThemHeld() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode before = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(before, "A1");
        long a2 = seatId(before, "A2");

        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1, a2)));

        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("held");
        assertThat(seatState(after, "A2")).isEqualTo("held");
        assertThat(seatState(after, "A3")).isEqualTo("free");
        assertThat(seatState(after, "B1")).isEqualTo("free");
    }

    @Test
    void requestForSevenSeatsWritesNoHolds() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode before = ada.get("/api/events/" + eventId).getBody();
        List<Long> seven = List.of(
                seatId(before, "A1"),
                seatId(before, "A2"),
                seatId(before, "A3"),
                seatId(before, "A4"),
                seatId(before, "A5"),
                seatId(before, "A6"),
                seatId(before, "A7"));

        ResponseEntity<JsonNode> refused = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", seven));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        for (String label : List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7")) {
            assertThat(seatState(after, label)).isEqualTo("free");
        }
    }

    @Test
    void requestAboveTheSeatsLeftOnTheOrderWritesNothingAndOneSeatStillCanBeHeld() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        ResponseEntity<JsonNode> first = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(
                        seatId(opened, "A1"),
                        seatId(opened, "A2"),
                        seatId(opened, "A3"),
                        seatId(opened, "A4"),
                        seatId(opened, "A5"))));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> twoMore = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A6"), seatId(opened, "A7"))));
        assertThat(twoMore.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode afterTwo = ada.get("/api/events/" + eventId).getBody();
        for (String label : List.of("A1", "A2", "A3", "A4", "A5")) {
            assertThat(seatState(afterTwo, label)).isEqualTo("held");
        }
        assertThat(seatState(afterTwo, "A6")).isEqualTo("free");
        assertThat(seatState(afterTwo, "A7")).isEqualTo("free");

        ResponseEntity<JsonNode> oneMore = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A6"))));
        assertThat(oneMore.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode afterOne = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(afterOne, "A6")).isEqualTo("held");
        assertThat(seatState(afterOne, "A7")).isEqualTo("free");
    }

    @Test
    void withElevenAlreadyCountingARequestForTwoWritesNothingAndOneWritesAHold() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> priorSeats = IntStream.rangeClosed(1, 11)
                .mapToObj(number -> seatId(opened, "A" + number))
                .toList();
        recordTicketsAlreadyCountingTowardTwelve(eventId, priorSeats);

        JsonNode withTickets = ada.get("/api/events/" + eventId).getBody();
        for (int number = 1; number <= 11; number++) {
            assertThat(seatState(withTickets, "A" + number)).isEqualTo("ticket");
        }

        ResponseEntity<JsonNode> two = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A12"), seatId(opened, "B1"))));
        assertThat(two.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode afterTwo = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(afterTwo, "A12")).isEqualTo("free");
        assertThat(seatState(afterTwo, "B1")).isEqualTo("free");
        assertThat(seatState(afterTwo, "A1")).isEqualTo("ticket");

        ResponseEntity<JsonNode> one = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A12"))));
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode afterOne = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(afterOne, "A12")).isEqualTo("held");
        assertThat(seatState(afterOne, "B1")).isEqualTo("free");
        assertThat(seatState(afterOne, "A11")).isEqualTo("ticket");
    }

    @Test
    void secondHoldFromTheSameCustomerWaitsAndKeepsNothingWhenTheFirstUsedTheLastSpot() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession firstSession = signIn("ada@example.com");
        ApiSession secondSession = signIn("ada@example.com");
        JsonNode opened = firstSession.get("/api/events/" + eventId).getBody();
        ResponseEntity<JsonNode> five = firstSession.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(
                        seatId(opened, "A1"),
                        seatId(opened, "A2"),
                        seatId(opened, "A3"),
                        seatId(opened, "A4"),
                        seatId(opened, "A5"))));
        assertThat(five.getStatusCode()).isEqualTo(HttpStatus.OK);

        pausingHoldGate.arm();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<JsonNode>> first = pool.submit(() -> firstSession.post(
                    "/api/events/" + eventId + "/holds",
                    java.util.Map.of("seatIds", List.of(seatId(opened, "A6")))));
            assertThat(pausingHoldGate.awaitFirstArrival(Duration.ofSeconds(10))).isTrue();
            Future<ResponseEntity<JsonNode>> second = pool.submit(() -> secondSession.post(
                    "/api/events/" + eventId + "/holds",
                    java.util.Map.of("seatIds", List.of(seatId(opened, "A7")))));
            awaitSecondDatabaseSessionWaitingOnALock();
            pausingHoldGate.release();

            assertThat(first.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            pausingHoldGate.release();
            pool.shutdownNow();
        }

        JsonNode after = firstSession.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A6")).isEqualTo("held");
        assertThat(seatState(after, "A7")).isEqualTo("free");
        for (String label : List.of("A1", "A2", "A3", "A4", "A5")) {
            assertThat(seatState(after, label)).isEqualTo("held");
        }
    }

    @Test
    void twoCustomersCannotBothHoldTheSameSeatAndThatSeatCanBeHeldForAnotherEvent() throws Exception {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");

        pausingHoldGate.armWriters(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ResponseEntity<JsonNode> adaHeld;
        ResponseEntity<JsonNode> graceHeld;
        try {
            Future<ResponseEntity<JsonNode>> adaFuture = pool.submit(() -> ada.post(
                    "/api/events/" + eventId + "/holds",
                    java.util.Map.of("seatIds", List.of(a1))));
            Future<ResponseEntity<JsonNode>> graceFuture = pool.submit(() -> grace.post(
                    "/api/events/" + eventId + "/holds",
                    java.util.Map.of("seatIds", List.of(a1))));
            assertThat(pausingHoldGate.awaitWriters(Duration.ofSeconds(10))).isTrue();
            awaitTwoDatabaseSessionsIdleInTransaction();
            pausingHoldGate.releaseWriters();
            adaHeld = adaFuture.get(15, TimeUnit.SECONDS);
            graceHeld = graceFuture.get(15, TimeUnit.SECONDS);
        } finally {
            pausingHoldGate.releaseWriters();
            pool.shutdownNow();
        }

        assertThat(List.of(adaHeld.getStatusCode(), graceHeld.getStatusCode()))
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A2")).isEqualTo("free");

        ApiSession refused = adaHeld.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT) ? ada : grace;
        long otherEventId = createEvent(admin);
        JsonNode other = refused.get("/api/events/" + otherEventId).getBody();
        assertThat(seatId(other, "A1")).isEqualTo(a1);
        ResponseEntity<JsonNode> otherHold = refused.post(
                "/api/events/" + otherEventId + "/holds",
                java.util.Map.of("seatIds", List.of(a1)));
        assertThat(otherHold.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(refused.get("/api/events/" + otherEventId).getBody(), "A1")).isEqualTo("held");
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
    }

    @Test
    void openingTheEventAfterTenMinutesFreesAHoldAndTheSeatCanBeHeldAgain() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ResponseEntity<JsonNode> moved = ada.post("/api/test/clock", java.util.Map.of(
                "instant", "2031-04-01T12:00:00Z"));
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A1"))));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> stillInside = ada.post("/api/test/clock", java.util.Map.of(
                "instant", "2031-04-01T12:09:59Z"));
        assertThat(stillInside.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");

        ResponseEntity<JsonNode> expired = ada.post("/api/test/clock", java.util.Map.of(
                "instant", "2031-04-01T12:10:00Z"));
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");

        ResponseEntity<JsonNode> again = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seatId(opened, "A1"))));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("held");
    }

    private void awaitSecondDatabaseSessionWaitingOnALock() throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (databaseSessionsWaitingOnALock() > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("The second hold never waited on a database lock");
    }

    private int databaseSessionsWaitingOnALock() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select count(*) from pg_stat_activity "
                                + "where datname = current_database() "
                                + "and wait_event_type = 'Lock' "
                                + "and pid <> pg_backend_pid()")) {
            if (!rows.next()) {
                return 0;
            }
            return rows.getInt(1);
        }
    }

    private void awaitTwoDatabaseSessionsIdleInTransaction() throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (databaseSessionsIdleInTransaction() >= 2) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("The two holds never overlapped as two database sessions");
    }

    private int databaseSessionsIdleInTransaction() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select count(*) from pg_stat_activity "
                                + "where datname = current_database() "
                                + "and state = 'idle in transaction' "
                                + "and pid <> pg_backend_pid()")) {
            if (!rows.next()) {
                return 0;
            }
            return rows.getInt(1);
        }
    }

    private void recordTicketsAlreadyCountingTowardTwelve(long eventId, List<Long> seatIds) {
        try (Connection connection = dataSource.getConnection()) {
            long customerId = queryLong(connection, "select id from account where email = ?", "ada@example.com");
            long firstOrder = insertClosedOrder(connection, customerId, eventId);
            long secondOrder = insertClosedOrder(connection, customerId, eventId);
            for (int index = 0; index < seatIds.size(); index++) {
                long orderId = index < 6 ? firstOrder : secondOrder;
                insertTicket(connection, orderId, eventId, seatIds.get(index));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long queryLong(Connection connection, String sql, String parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("Missing row");
                }
                return rows.getLong(1);
            }
        }
    }

    private static long insertClosedOrder(Connection connection, long customerId, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into customer_order (customer_id, event_id, opened_at, open_for_holds) values (?, ?, ?, false)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.setLong(2, eventId);
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Order id was not returned");
                }
                return keys.getLong(1);
            }
        }
    }

    private static void insertTicket(Connection connection, long orderId, long eventId, long seatId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into event_seat (order_id, event_id, seat_id, kind, active) values (?, ?, ?, 'TICKET', true)")) {
            statement.setLong(1, orderId);
            statement.setLong(2, eventId);
            statement.setLong(3, seatId);
            statement.executeUpdate();
        }
    }

    private long createEvent(ApiSession admin) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "HoldNight-" + UUID.randomUUID();
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
        for (JsonNode section : event.get("sections")) {
            for (JsonNode seat : section.get("seats")) {
                if (label.equals(seat.get("label").asText())) {
                    return seat.get("state").asText();
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
