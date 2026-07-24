package com.townai.line.dispatcher;

import com.sun.net.httpserver.HttpServer;
import com.townai.line.config.LineTaskProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalLineEventDispatcherTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsEventIdToLocalTaskEndpoint() throws IOException {
        AtomicReference<String> method = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/internal/tasks/line-events/event-1",
                exchange -> {
                    method.set(exchange.getRequestMethod());
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                }
        );
        server.start();
        LocalLineEventDispatcher dispatcher = dispatcher();

        dispatcher.dispatch("event-1");

        assertEquals("POST", method.get());
    }

    @Test
    void throwsDispatchExceptionForNonSuccessResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/internal/tasks/line-events/event-1",
                exchange -> {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                }
        );
        server.start();
        LocalLineEventDispatcher dispatcher = dispatcher();

        assertThrows(
                LineEventDispatchException.class,
                () -> dispatcher.dispatch("event-1")
        );
    }

    private LocalLineEventDispatcher dispatcher() {
        String targetUrl = "http://localhost:"
                + server.getAddress().getPort()
                + "/internal/tasks/line-events";
        return new LocalLineEventDispatcher(
                RestClient.builder(),
                properties(targetUrl)
        );
    }

    private LineTaskProperties properties(String localTargetUrl) {
        return new LineTaskProperties(
                "local",
                localTargetUrl,
                "",
                "asia-northeast1",
                "line-events",
                "",
                "",
                ""
        );
    }
}
