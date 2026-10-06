package vn.qlwebsite.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AlertProxyTest {
    private static final HttpServer BACKEND;
    private static final AtomicReference<String> query = new AtomicReference<>();
    private static final AtomicInteger responseStatus = new AtomicInteger(200);

    static {
        try {
            BACKEND = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            BACKEND.createContext("/api/alerts", exchange -> {
                query.set(exchange.getRequestURI().getRawQuery());
                byte[] body = "{\"content\":[],\"number\":1,\"totalElements\":0}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus.get(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            BACKEND.start();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void backend(DynamicPropertyRegistry registry) {
        registry.add("app.backend.url", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.stop(0);
    }

    @Autowired private MockMvc mvc;

    @Test
    void forwardsAlertsAndPaginationQueryToBackend() throws Exception {
        responseStatus.set(200);
        mvc.perform(get("/backend-api/alerts?page=1&size=2&eventType=DOWN&websiteId=7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(1));
        assertEquals("page=1&size=2&eventType=DOWN&websiteId=7", query.get());
    }

    @Test
    void surfacesBackendFailure() throws Exception {
        responseStatus.set(503);
        try {
            mvc.perform(get("/backend-api/alerts")).andExpect(status().isServiceUnavailable());
        } finally {
            responseStatus.set(200);
        }
    }

    @Test
    void alertPageUsesRealBackendByDefault() throws Exception {
        mvc.perform(get("/alerts")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-demo=\"false\"")))
                .andExpect(content().string(containsString("data-backend=\"true\"")));
        mvc.perform(get("/backend-api/unknown")).andExpect(status().isNotFound());
    }
}
