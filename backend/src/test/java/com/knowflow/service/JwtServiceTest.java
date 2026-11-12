package com.knowflow.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String TEST_SECRET = "knowflow-test-jwt-secret-key-32bytes!!";

    @Test
    void createTokenUsesUserIdAsSubject() {
        JwtService jwtService = new JwtService(TEST_SECRET, 3600);

        String token = jwtService.createToken(42L);

        assertEquals(42L, jwtService.parseUserId(token));
    }

    @Test
    void parseUserIdRejectsInvalidToken() {
        JwtService jwtService = new JwtService(TEST_SECRET, 3600);

        assertThrows(JwtService.InvalidTokenException.class, () -> jwtService.parseUserId("not-a-jwt"));
    }

    @Test
    void parseUserIdRejectsExpiredToken() throws InterruptedException {
        JwtService jwtService = new JwtService(TEST_SECRET, 1);
        String token = jwtService.createToken(7L);
        Thread.sleep(1100);

        assertThrows(JwtService.ExpiredTokenException.class, () -> jwtService.parseUserId(token));
    }
}
