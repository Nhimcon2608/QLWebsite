package vn.qlwebsite.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AlertProxyTest {
  private static final HttpServer BACKEND;
  private static final AtomicReference<String> cookie = new AtomicReference<>();
  private static final AtomicReference<String> csrf = new AtomicReference<>();
  private static final AtomicReference<String> method = new AtomicReference<>();
  private static final AtomicReference<String> query = new AtomicReference<>();
  private static final AtomicInteger responseStatus = new AtomicInteger(200);

  static {
    try {
      BACKEND = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      BACKEND.createContext(
          "/api/alerts",
          exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body =
                "{\"content\":[],\"number\":1,\"totalElements\":0}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      BACKEND.createContext(
          "/api/session",
          exchange -> {
            cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            csrf.set(exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"));
            method.set(exchange.getRequestMethod());
            exchange
                .getResponseHeaders()
                .add("Set-Cookie", "QLSESSION=testsession123; Path=/; HttpOnly; SameSite=Lax");
            byte[] body = "{\"username\":\"admin\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      BACKEND.createContext(
          "/api/reports/export",
          exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/csv;charset=UTF-8");
            exchange
                .getResponseHeaders()
                .add("Content-Disposition", "attachment; filename=report.csv");
            byte[] body = "name,url\r\nSite,https://example.com".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      BACKEND.createContext(
          "/api/websites/7/link-scans",
          exchange -> {
            query.set(exchange.getRequestURI().toString());
            method.set(exchange.getRequestMethod());
            csrf.set(exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"));
            byte[] body = "{\"scan\":{\"id\":9},\"results\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(
                exchange.getRequestMethod().equals("POST") ? 202 : 200, body.length);
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
  void retiredNotificationPageRedirectsAndProxyRejectsItsApi() throws Exception {
    mvc.perform(get("/settings"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/alerts"))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/backend-api/notifications/settings")).andExpect(status().isNotFound());
    mvc.perform(post("/backend-api/notifications/test")).andExpect(status().isNotFound());
    mvc.perform(get("/backend-api/notifications/deliveries")).andExpect(status().isNotFound());
  }

  @Test
  void forwardsAlertsAndPaginationQueryToBackend() throws Exception {
    responseStatus.set(200);
    mvc.perform(get("/backend-api/alerts?page=1&size=2&eventType=DOWN&websiteId=7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(1));
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
    mvc.perform(get("/alerts"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-demo=\"false\"")))
        .andExpect(content().string(containsString("data-backend=\"true\"")));
    mvc.perform(get("/backend-api/unknown")).andExpect(status().isNotFound());
  }

  @Test
  void forwardsOnlyBackendSessionCookieAndCsrfAndReturnsSessionCookie() throws Exception {
    mvc.perform(
            post("/backend-api/session")
                .cookie(
                    new jakarta.servlet.http.Cookie("QLSESSION", "abc123"),
                    new jakarta.servlet.http.Cookie("other", "private"))
                .header("X-CSRF-TOKEN", "csrf-test")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Set-Cookie", containsString("QLSESSION=testsession123")));
    assertEquals("QLSESSION=abc123", cookie.get());
    assertEquals("csrf-test", csrf.get());
    assertEquals("POST", method.get());
  }

  @Test
  void preservesCsvDownloadHeadersAndContent() throws Exception {
    mvc.perform(get("/backend-api/reports/export?websiteId=1"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(content().string(containsString("Site,https://example.com")));
  }

  @Test
  void newPagesRenderAndArbitraryProxyPathsAreDenied() throws Exception {
    for (String path : new String[] {"/users", "/login"})
      mvc.perform(get(path)).andExpect(status().isOk());
    mvc.perform(get("/backend-api/actuator/env")).andExpect(status().isNotFound());
    mvc.perform(get("/backend-api/public/status")).andExpect(status().isNotFound());
    mvc.perform(get("/status"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/websites"));
  }

  @Test
  void forwardsLinkScanCreationAndReport() throws Exception {
    mvc.perform(
            post("/backend-api/websites/7/link-scans")
                .header("X-CSRF-TOKEN", "scan-csrf")
                .contentType("application/json")
                .content("{\"maxPages\":5}"))
        .andExpect(status().isAccepted());
    assertEquals("POST", method.get());
    assertEquals("scan-csrf", csrf.get());
    mvc.perform(get("/backend-api/websites/7/link-scans/9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scan.id").value(9));
    assertEquals("/api/websites/7/link-scans/9", query.get());
    mvc.perform(get("/backend-api/websites/7/link-scans/arbitrary"))
        .andExpect(status().isNotFound());
  }
}
