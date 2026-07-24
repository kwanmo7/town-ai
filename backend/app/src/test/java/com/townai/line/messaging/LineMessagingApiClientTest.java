package com.townai.line.messaging;

import com.sun.net.httpserver.HttpServer;
import com.townai.line.config.LineMessagingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineMessagingApiClientTest {

    private static final UUID RETRY_KEY = UUID.fromString(
            "123e4567-e89b-52d3-a456-426614174000"
    );

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerTokenRetryKeyAndJsonBody() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> retryKey = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst(
                    "Authorization"
            ));
            retryKey.set(exchange.getRequestHeaders().getFirst(
                    "X-Line-Retry-Key"
            ));
            body.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            exchange.sendResponseHeaders(200, -1);
        });
        LineMessagingApiClient client = client("channel-token");

        client.push(request(), RETRY_KEY);

        assertEquals("Bearer channel-token", authorization.get());
        assertEquals(RETRY_KEY.toString(), retryKey.get());
        assertTrue(body.get().contains("\"to\":\"user-1\""));
        assertTrue(body.get().contains("\"type\":\"text\""));
    }

    @Test
    void acceptsConflictWhenRetryKeyWasPreviouslyAccepted()
            throws IOException {
        startServer(exchange -> {
            exchange.getResponseHeaders().add(
                    "X-Line-Accepted-Request-Id",
                    "accepted-request"
            );
            exchange.sendResponseHeaders(409, -1);
        });
        LineMessagingApiClient client = client("channel-token");

        assertDoesNotThrow(() -> client.push(request(), RETRY_KEY));
    }

    @Test
    void classifiesServerFailureAsRetryable() throws IOException {
        startServer(exchange -> exchange.sendResponseHeaders(500, -1));
        LineMessagingApiClient client = client("channel-token");

        LineMessagingException exception = assertThrows(
                LineMessagingException.class,
                () -> client.push(request(), RETRY_KEY)
        );

        assertTrue(exception.retryable());
        assertEquals(
                "LINE_PUSH_TEMPORARY_ERROR",
                exception.errorCode()
        );
    }

    @Test
    void classifiesInvalidRequestAsPermanent() throws IOException {
        startServer(exchange -> exchange.sendResponseHeaders(400, -1));
        LineMessagingApiClient client = client("channel-token");

        LineMessagingException exception = assertThrows(
                LineMessagingException.class,
                () -> client.push(request(), RETRY_KEY)
        );

        assertFalse(exception.retryable());
        assertEquals("LINE_PUSH_REJECTED", exception.errorCode());
    }

    @Test
    void reportsMissingChannelTokenAsNotConfigured() throws IOException {
        startServer(exchange -> exchange.sendResponseHeaders(200, -1));
        LineMessagingApiClient client = client("");

        assertFalse(client.isConfigured());
        assertThrows(
                LineMessagingException.class,
                () -> client.push(request(), RETRY_KEY)
        );
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/v2/bot/message/push",
                exchange -> {
                    try {
                        handler.handle(exchange);
                    } finally {
                        exchange.close();
                    }
                }
        );
        server.start();
    }

    private LineMessagingApiClient client(String token) {
        String baseUrl = "http://localhost:"
                + server.getAddress().getPort();
        return new LineMessagingApiClient(
                RestClient.builder(),
                new LineMessagingProperties(
                        token,
                        baseUrl,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2)
                )
        );
    }

    private LinePushRequest request() {
        return new LinePushRequest(
                "user-1",
                List.of(LinePushRequest.TextMessage.of("테스트"))
        );
    }

    @FunctionalInterface
    private interface ExchangeHandler {

        void handle(com.sun.net.httpserver.HttpExchange exchange)
                throws IOException;
    }
}
