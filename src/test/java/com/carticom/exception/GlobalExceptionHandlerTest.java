package com.carticom.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link GlobalExceptionHandler} maps
 * {@link MaxUploadSizeExceededException} to a 400 with a friendly message.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void maxUploadSizeReturns400WithFriendlyMessage() {
        MaxUploadSizeExceededException ex =
                new MaxUploadSizeExceededException(10 * 1024 * 1024);

        ResponseEntity<Map<String, Object>> response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("File is too large. Maximum size is 10MB.",
                response.getBody().get("message"));
    }

    @Test
    void maxUploadSizeBodyContainsTimestamp() {
        MaxUploadSizeExceededException ex =
                new MaxUploadSizeExceededException(5 * 1024 * 1024);

        ResponseEntity<Map<String, Object>> response = handler.handleMaxUploadSize(ex);

        assertTrue(response.getBody().containsKey("timestamp"));
    }
}
