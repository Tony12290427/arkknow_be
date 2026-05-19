package com.arknow.knowpost;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeedSmokeTest {
    @Autowired private TestRestTemplate restTemplate;

    @Test
    void feedEndpointReturnsData() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/knowposts/feed?page=1&size=5", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"page\""));
    }
}
