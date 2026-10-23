package com.mycompany.do_an_ltm_2380600550;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import com.mycompany.do_an_ltm_2380600550.service.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    classes = Do_An_LTM_2380600550.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:advanced;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.show-sql=false",
      "app.jobs.enabled=false",
      "logging.level.org.hibernate.SQL=OFF"
    })
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class AdvancedChecksTest {
  @Autowired WebsiteService service;
  @Autowired WebsiteRepository websites;
  @Autowired UptimeCheckLogRepository logs;
  @Autowired WebsiteAlertRepository alerts;
  @Autowired IncidentRepository incidents;
  @Autowired MaintenanceWindowRepository maintenance;
  @Autowired LinkScanService scans;
  @Autowired LinkScanRepository scanRepo;
  @Autowired ReportingService reporting;
  @Autowired NotificationDeliveryRepository deliveries;
  @Autowired NotificationSettingsRepository notificationSettings;
  @Autowired NotificationService notifications;
  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired ObjectMapper json;
  @MockitoSpyBean NetworkChecks network;
  HttpServer server;
  ExecutorService serverWorkers;
  String base;

  @BeforeEach
  void setup() throws Exception {
    if (users.findByUsername("viewer").isEmpty()) {
      var u = new AppUser();
      u.setUsername("viewer");
      u.setRole("VIEWER");
      u.setPasswordHash("unused");
      users.save(u);
    }
    scanRepo.deleteAll();
    deliveries.deleteAll();
    notificationSettings.deleteAll();
    maintenance.deleteAll();
    incidents.deleteAll();
    alerts.deleteAll();
    logs.deleteAll();
    websites.deleteAll();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    serverWorkers = Executors.newCachedThreadPool();
    server.setExecutor(serverWorkers);
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
    serverWorkers.shutdownNow();
  }

  void response(String path, int code, String type, String text) {
    server.createContext(
        path,
        e -> {
          var bytes = text.getBytes(StandardCharsets.UTF_8);
          e.getResponseHeaders().set("Content-Type", type);
          e.sendResponseHeaders(code, e.getRequestMethod().equals("HEAD") ? -1 : bytes.length);
          if (!e.getRequestMethod().equals("HEAD")) e.getResponseBody().write(bytes);
          e.close();
        });
  }

  Website monitor(String path) {
    return service.create(new Website("Test", base + path, 10, true));
  }

  Website save(Website w) {
    return service.update(w.getId(), w);
  }

  void failed(UptimeCheckLog result, String type) {
    assertFalse(result.getIsUp());
    assertEquals(type, result.getErrorType());
  }

  @Test
  void retiredExternalChannelsDoNotQueueOrSendAndKeepInAppAlerts() throws Exception {
    var settings = new NotificationSettings();
    settings.setEmailEnabled(true);
    settings.setEmailTo("legacy@example.invalid");
    settings.setTelegramEnabled(true);
    settings.setTelegramChatId("123");
    notificationSettings.save(settings);
    notifications.enqueue("Test", "Must not be sent");
    assertEquals(0, deliveries.count());
    assertFalse(notifications.smtpConfigured());
    assertFalse(notifications.telegramConfigured());
    assertEquals(410, assertThrows(ResponseStatusException.class,
        () -> notifications.save(settings)).getStatusCode().value());

    var pending = new NotificationDelivery();
    pending.setChannel("EMAIL");
    deliveries.save(pending);
    var sent = new NotificationDelivery();
    sent.setChannel("EMAIL");
    sent.setDeliveryStatus("SENT");
    deliveries.save(sent);
    notifications.run(null);
    assertEquals("CANCELLED", deliveries.findById(pending.getId()).orElseThrow().getDeliveryStatus());
    assertEquals("SENT", deliveries.findById(sent.getId()).orElseThrow().getDeliveryStatus());
    notifications.deliverPending();
    assertEquals(0, deliveries.findById(pending.getId()).orElseThrow().getAttempts());

    response("/", 503, "text/plain", "down");
    var w = monitor("/");
    service.check(w.getId(), "MANUAL");
    assertEquals(1, alerts.count());
    assertEquals(1, incidents.count());
    assertEquals(2, deliveries.count());
    mvc.perform(get("/api/notifications/settings")).andExpect(status().isNotFound());
    mvc.perform(post("/api/notifications/test").with(csrf())).andExpect(status().isNotFound());
  }

  @Test
  void keywordIsCaseInsensitiveByDefaultAndFeedsIncidents() {
    response("/", 200, "text/html; charset=UTF-8", "<h1>Service SUCCESS đang hoạt động</h1>");
    var w = monitor("/");
    w.setKeywordMode("CONTAINS");
    w.setKeyword("success");
    w = save(w);
    assertTrue(service.check(w.getId(), "SCHEDULED").getIsUp());
    w.setKeywordCaseSensitive(true);
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "KEYWORD_MISSING");
    assertEquals(1, incidents.count());
    w.setKeyword("SUCCESS");
    w = save(w);
    assertTrue(service.check(w.getId(), "MANUAL").getIsUp());
    assertEquals("RESOLVED", incidents.findAll().get(0).getStatus());
    assertEquals(2, alerts.count());
  }

  @Test
  void forbiddenKeywordAndHttpStatusTakePriority() {
    response("/", 200, "text/plain", "Fatal Error");
    var w = monitor("/");
    w.setKeywordMode("NOT_CONTAINS");
    w.setKeyword("error");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "KEYWORD_FORBIDDEN");
    w.setKeyword("unavailable");
    w = save(w);
    assertTrue(service.check(w.getId(), "MANUAL").getIsUp());
    response("/bad", 503, "text/plain", "success");
    w.setUrl(base + "/bad");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "HTTP_ERROR");
  }

  @Test
  void jsonPointerHandlesNestedArraysEscapesAndTypedEquality() {
    response(
        "/",
        200,
        "application/json",
        "{\"data\":{\"items\":[{\"a/b~c\":true}],\"nullable\":null,\"code\":200}}");
    var w = monitor("/");
    w.setJsonEnabled(true);
    w.setJsonPointer("/data/items/0/a~1b~0c");
    w.setJsonExpected("true");
    w = save(w);
    assertTrue(service.check(w.getId(), "MANUAL").getIsUp());
    w.setJsonExpected("\"true\"");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "JSON_VALUE_MISMATCH");
    w.setJsonPointer("/data/nullable");
    w.setJsonExpected("null");
    w = save(w);
    assertTrue(service.check(w.getId(), "MANUAL").getIsUp());
    w.setJsonComparison("EXISTS");
    w.setJsonPointer("/missing");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "JSON_PATH_MISSING");
    w.setJsonPointer("");
    w = save(w);
    assertTrue(service.check(w.getId(), "MANUAL").getIsUp());
  }

  @Test
  void rejectsInvalidJsonResponsesAndRequiresBothAssertions() {
    response("/", 200, "application/json", "{\"status\":\"ok\"}");
    var w = monitor("/");
    w.setJsonEnabled(true);
    w.setJsonExpected("\"ok\"");
    w.setJsonPointer("/status");
    w.setKeywordMode("CONTAINS");
    w.setKeyword("missing");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "KEYWORD_MISSING");
    w.setKeyword("status");
    w = save(w);
    assertTrue(service.check(w.getId(), "MANUAL").getIsUp());
    response("/html", 200, "text/html", "not json");
    w.setUrl(base + "/html");
    w.setKeywordMode("OFF");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "INVALID_JSON");
  }

  @Test
  void validatesConfigurationAndDoesNotOverwriteSavedSettingsOnInvalidUpdate() throws Exception {
    var w = monitor("/");
    w.setJsonEnabled(true);
    w.setJsonExpected("plain string");
    assertThrows(ResponseStatusException.class, () -> save(w));
    w.setJsonExpected("true false");
    assertThrows(ResponseStatusException.class, () -> save(w));
    w.setJsonExpected("true");
    w.setJsonPointer("/invalid~2escape");
    assertThrows(ResponseStatusException.class, () -> save(w));
    w.setJsonPointer("data.status");
    assertThrows(ResponseStatusException.class, () -> save(w));
    w.setJsonEnabled(false);
    w.setCheckType("TCP");
    w.setTcpPort(65536);
    assertThrows(ResponseStatusException.class, () -> save(w));
    assertEquals("HTTP", websites.findById(w.getId()).orElseThrow().getCheckType());
    mvc.perform(
            put("/api/websites/" + w.getId())
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(w)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void capsContentBodyAndTimesOutDuringStreaming() {
    response("/large", 200, "text/plain", "x".repeat(2 * 1024 * 1024 + 100));
    var w = monitor("/large");
    w.setKeywordMode("CONTAINS");
    w.setKeyword("x");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "BODY_TOO_LARGE");
    server.createContext(
        "/slow",
        e -> {
          e.sendResponseHeaders(200, 10);
          try {
            Thread.sleep(800);
            e.getResponseBody().write("1234567890".getBytes());
          } catch (Exception ignored) {
          } finally {
            e.close();
          }
        });
    w.setUrl(base + "/slow");
    w.setTimeoutMs(150);
    w.setSlowThresholdMs(50);
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "TIMEOUT");
    w.setKeywordMode("OFF");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "TIMEOUT");
  }

  @Test
  void dnsResolvesRealLocalAddressAndValidatesExpectedIp() {
    var w = monitor("/");
    w.setCheckType("DNS");
    w.setDnsExpected("127.0.0.1");
    w = save(w);
    var result = service.check(w.getId(), "SCHEDULED");
    assertTrue(result.getIsUp());
    assertNull(result.getStatusCode());
    assertTrue(result.getCheckDetails().contains("127.0.0.1"));
    w.setDnsExpected("192.0.2.1");
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "DNS_MISMATCH");
    w.setDnsExpected("example.com");
    Website invalid = w;
    assertThrows(ResponseStatusException.class, () -> save(invalid));
  }

  @Test
  void dnsDistinguishesEmptyResultFailureAndTimeout() throws Exception {
    var w = monitor("/");
    w.setCheckType("DNS");
    w.setDnsRecordType("AAAA");
    w = save(w);
    doReturn(List.of()).when(network).resolve(anyString(), eq("AAAA"), anyInt());
    failed(service.check(w.getId(), "MANUAL"), "DNS_NO_RECORDS");
    doThrow(new TimeoutException()).when(network).resolve(anyString(), eq("AAAA"), anyInt());
    failed(service.check(w.getId(), "MANUAL"), "DNS_TIMEOUT");
    doThrow(new UnknownHostException()).when(network).resolve(anyString(), eq("AAAA"), anyInt());
    failed(service.check(w.getId(), "MANUAL"), "DNS_ERROR");
  }

  @Test
  void tcpConnectsAndReportsClosedPortAndIncludesLatencyInMetrics() throws Exception {
    var w = monitor("/");
    w.setCheckType("TCP");
    w.setTcpPort(server.getAddress().getPort());
    w = save(w);
    var result = service.check(w.getId(), "MANUAL");
    assertTrue(result.getIsUp());
    assertEquals("TCP", result.getCheckType());
    assertNotNull(ReportingService.log(result).get("responseTimeMs"));
    var metrics = reporting.metrics(w.getId(), reporting.range(null, null), null);
    assertNotNull(metrics.get("averageResponseTimeMs"));
    try (var socket = new ServerSocket(0)) {
      w.setTcpPort(socket.getLocalPort());
    }
    w = save(w);
    failed(service.check(w.getId(), "MANUAL"), "TCP_CONNECTION_FAILED");
  }

  @Test
  void permitsParallelTypesButRejectsDuplicateTargetAndResetsTypeState() {
    response("/", 503, "text/plain", "bad");
    var w = monitor("/");
    service.check(w.getId(), "MANUAL");
    var dns = new Website("DNS", base + "/", 10, true);
    dns.setCheckType("DNS");
    service.create(dns);
    assertThrows(ResponseStatusException.class, () -> service.create(dns));
    w.setCheckType("TCP");
    w.setTcpPort(server.getAddress().getPort());
    w = save(w);
    assertEquals("UNKNOWN", w.getMonitoringStatus());
    assertEquals("MONITORING_STOPPED", incidents.findAll().get(0).getStatus());
  }

  LinkScan awaitScan(LinkScan initial) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
    while (System.nanoTime() < deadline) {
      var s = scanRepo.findById(initial.getId()).orElseThrow();
      if (!Set.of("RUNNING", "QUEUED").contains(s.getStatus())) return s;
      Thread.sleep(20);
    }
    fail("Scan did not finish in time");
    return null;
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> results(Website w, LinkScan s) {
    return (List<Map<String, Object>>) scans.report(w.getId(), s.getId()).get("results");
  }

  @Test
  void scannerResolvesRelativeUrlsDeduplicatesAndHandlesRedirectsAndReviewStatuses()
      throws Exception {
    response(
        "/",
        200,
        "text/html",
        "<a href='/ok'>OK</a><a href='/ok#part'>duplicate</a><a href='/missing'>404</a><a"
            + " href='/redirect'>redirect</a><a href='/blocked'>403</a><a href='/limited'>429</a><a"
            + " href='mailto:a@b.com'>skip</a><a href='/encoded%20path'>encoded</a>");
    response("/ok", 200, "text/html", "<a href='/child'>child</a>");
    response("/child", 200, "text/plain", "ok");
    response("/missing", 404, "text/plain", "gone");
    response("/blocked", 403, "text/plain", "blocked");
    response("/limited", 429, "text/plain", "limited");
    response("/encoded%20path", 200, "text/plain", "ok");
    server.createContext(
        "/redirect",
        e -> {
          e.getResponseHeaders().set("Location", "/ok");
          e.sendResponseHeaders(302, -1);
          e.close();
        });
    var w = monitor("/");
    var scan = awaitScan(scans.start(w.getId(), 5, 50, false));
    var rows = results(w, scan);
    assertEquals("COMPLETED", scan.getStatus());
    assertEquals(1, scan.getBroken());
    assertEquals(2, scan.getUncertain());
    assertEquals(1, rows.stream().filter(r -> r.get("url").equals(base + "/ok")).count());
    assertTrue(
        rows.stream()
            .anyMatch(
                r ->
                    r.get("url").equals(base + "/child")
                        && r.get("sourceUrl").equals(base + "/ok")));
    assertTrue(
        rows.stream()
            .anyMatch(
                r ->
                    r.get("url").equals(base + "/redirect")
                        && r.get("finalUrl").equals(base + "/ok")));
    assertTrue(rows.stream().anyMatch(r -> r.get("url").equals(base + "/encoded%20path")));
    assertEquals(0, logs.count());
    assertEquals(0, incidents.count());
  }

  @Test
  void scannerHonorsLimitsAndExternalScopeAndFallsBackFromHead() throws Exception {
    AtomicInteger externalHits = new AtomicInteger();
    var outside = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    outside.createContext(
        "/",
        e -> {
          externalHits.incrementAndGet();
          if (e.getRequestMethod().equals("HEAD")) {
            e.sendResponseHeaders(405, -1);
          } else {
            e.sendResponseHeaders(200, -1);
          }
          e.close();
        });
    outside.start();
    try {
      response(
          "/",
          200,
          "text/html",
          "<a href='http://127.0.0.1:"
              + outside.getAddress().getPort()
              + "/'>external</a><a href='/a'>a</a><a href='/b'>b</a>");
      response("/a", 200, "text/html", "<a href='/deeper'>more</a>");
      response("/b", 200, "text/plain", "b");
      var w = monitor("/");
      var scan = awaitScan(scans.start(w.getId(), 1, 20, false));
      assertEquals(0, externalHits.get());
      assertEquals("PARTIAL", scan.getStatus());
      scan = awaitScan(scans.start(w.getId(), 1, 20, true));
      assertEquals(2, externalHits.get());
      assertTrue(
          results(w, scan).stream()
              .anyMatch(
                  r ->
                      r.get("url").toString().contains(":" + outside.getAddress().getPort())
                          && r.get("status").equals("OK")));
      scan = awaitScan(scans.start(w.getId(), 1, 1, false));
      assertEquals("PARTIAL", scan.getStatus());
      assertEquals(1, scan.getChecked());
    } finally {
      outside.stop(0);
    }
  }

  @Test
  void scannerReportsRedirectLoopsAndFailuresWithoutAffectingUptime() throws Exception {
    response("/", 200, "text/html", "<a href='/loop'>loop</a>");
    server.createContext(
        "/loop",
        e -> {
          e.getResponseHeaders().set("Location", "/loop");
          e.sendResponseHeaders(302, -1);
          e.close();
        });
    var w = monitor("/");
    var scan = awaitScan(scans.start(w.getId(), 1, 20, false));
    assertEquals(1, scan.getUncertain());
    assertTrue(results(w, scan).stream().anyMatch(r -> r.get("status").equals("ERROR")));
    response("/root-bad", 500, "text/plain", "down");
    w.setUrl(base + "/root-bad");
    w = save(w);
    scan = awaitScan(scans.start(w.getId(), 1, 10, false));
    assertEquals("FAILED", scan.getStatus());
    assertEquals(0, logs.count());
  }

  @Test
  void scanEndpointsRequireCsrfAndViewerCannotStartButCanRead() throws Exception {
    response("/", 200, "text/html", "<p>No links</p>");
    var w = monitor("/");
    String route = "/api/websites/" + w.getId() + "/link-scans";
    mvc.perform(post(route).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(route)
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    var response =
        mvc.perform(
                post(route)
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"maxPages\":1,\"maxLinks\":10}"))
            .andExpect(status().isAccepted())
            .andReturn();
    var scan =
        awaitScan(
            scanRepo
                .findById(
                    json.readTree(response.getResponse().getContentAsString()).get("id").asLong())
                .orElseThrow());
    mvc.perform(get(route + "/" + scan.getId()).with(user("viewer").roles("VIEWER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scan.status").value("COMPLETED"))
        .andExpect(jsonPath("$.results[0].status").value("OK"));
    var other = monitor("/other");
    mvc.perform(get("/api/websites/" + other.getId() + "/link-scans/" + scan.getId()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsConcurrentScanAndInvalidLimits() throws Exception {
    server.createContext(
        "/",
        e -> {
          try {
            Thread.sleep(300);
          } catch (InterruptedException ignored) {
          }
          e.getResponseHeaders().set("Content-Type", "text/html");
          e.sendResponseHeaders(200, -1);
          e.close();
        });
    var w = monitor("/");
    assertThrows(ResponseStatusException.class, () -> scans.start(w.getId(), 21, 200, false));
    var first = scans.start(w.getId(), 1, 10, false);
    assertThrows(ResponseStatusException.class, () -> scans.start(w.getId(), 1, 10, false));
    awaitScan(first);
  }
}
