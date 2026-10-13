package com.example.exam.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.exam.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessErrorKeepsItsStatusAndReadableMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(
                new BusinessException(409, "题库数量不足"));

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().code());
        assertEquals("题库数量不足", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void unexpectedErrorReturnsGenericMessageAndLogsStackTrace() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level oldLevel = logger.getLevel();
        logger.setLevel(Level.ERROR);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/statistics/student");
            IllegalStateException failure = new IllegalStateException("internal-db-detail");

            ResponseEntity<ApiResponse<Void>> response = handler.handleException(failure, request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(500, response.getBody().code());
            assertEquals("服务器内部错误", response.getBody().message());
            assertFalse(response.getBody().message().contains("internal-db-detail"));
            assertFalse(appender.list.isEmpty(), "unexpected exceptions must be logged");
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.ERROR, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("GET /api/v1/statistics/student"));
            assertNotNull(event.getThrowableProxy(), "stack trace should be attached to the log event");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
            appender.stop();
        }
    }
}
