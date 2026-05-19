package com.arknow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSmokeTest {
    @Autowired private TestRestTemplate restTemplate;

    @Test
    void sendCodeShouldReturn200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(
            "{\"identifierType\":\"PHONE\",\"identifier\":\"13999999999\",\"scene\":\"REGISTER\"}", headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/v1/auth/send-code", request, Map.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }
}
