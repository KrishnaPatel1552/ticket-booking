package com.copart.prep;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
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
class CustomerApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void customerSignsInWithEmailAndPassword() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", "ada@example.com",
                "password", "password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo("ada@example.com");
        assertThat(response.getBody().get("role").asText()).isEqualTo("customer");

        ResponseEntity<JsonNode> me = session.get("/api/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email").asText()).isEqualTo("ada@example.com");
        assertThat(me.getBody().get("role").asText()).isEqualTo("customer");
    }

    @Test
    void wrongPasswordLeavesCustomerSignedOut() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", "ada@example.com",
                "password", "wrong-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> me = session.get("/api/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void secondCustomerSignsInAsACustomer() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", "grace@example.com",
                "password", "password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo("grace@example.com");
        assertThat(response.getBody().get("role").asText()).isEqualTo("customer");
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
