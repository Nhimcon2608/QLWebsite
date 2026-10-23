package com.mycompany.do_an_ltm_2380600550;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.do_an_ltm_2380600550.config.MonitoringJobs;
import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import com.mycompany.do_an_ltm_2380600550.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = Do_An_LTM_2380600550.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:features;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.show-sql=false",
      "app.jobs.enabled=false",
      "app.notifications.external-enabled=true",
      "spring.mail.host=mock.local",
      "logging.level.org.hibernate.SQL=OFF"
    })
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class MonitoringFeaturesTest {
  @Autowired WebsiteService service;
  @Autowired WebsiteRepository websites;
  @Autowired UptimeCheckLogRepository logs;
  @MockitoSpyBean WebsiteAlertRepository alerts;
  @Autowired IncidentRepository incidents;
  @Autowired MaintenanceWindowRepository maintenance;
  @Autowired AppUserRepository users;
  @Autowired NotificationSettingsRepository settings;
  @Autowired NotificationDeliveryRepository deliveries;
  @Autowired NotificationService notifications;
  @Autowired CsvImportService csv;
  @Autowired ReportingService reports;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder encoder;
  @Autowired com.mycompany.do_an_ltm_2380600550.config.DefaultAdminInitializer defaultAdmin;
  @MockitoBean JavaMailSender sender;

  @MockitoSpyBean(name = "monitorHttpClient")
  HttpClient http;

  HttpServer server;
  AtomicInteger status = new AtomicInteger(200);
  AtomicInteger delay = new AtomicInteger();
  AtomicInteger requests = new AtomicInteger();

  @BeforeEach
  void setup() throws Exception {
    deliveries.deleteAll();
    settings.deleteAll();
    maintenance.deleteAll();
    incidents.deleteAll();
    alerts.deleteAll();
    logs.deleteAll();
    websites.deleteAll();
    users.deleteAll();
    addUser("admin", "ADMIN");
    addUser("viewer", "VIEWER");
    addUser("operator", "OPERATOR");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        e -> {
          requests.incrementAndGet();
          try {
            Thread.sleep(delay.get());
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          e.sendResponseHeaders(status.get(), -1);
          e.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  AppUser addUser(String name, String role) {
    var u = new AppUser();
    u.setUsername(name);
    u.setRole(role);
    u.setPasswordHash(encoder.encode("StrongPassword123"));
    return users.save(u);
  }

  Website website() {
    return websites.save(
        new Website("Dịch vụ thử", "http://127.0.0.1:" + server.getAddress().getPort(), 10, true));
  }

  Website fresh(Website w) {
    return websites.findById(w.getId()).orElseThrow();
  }

  String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  void enableEmail() {
    var s = new NotificationSettings();
    s.setEmailEnabled(true);
    s.setEmailTo("test@example.invalid");
    notifications.save(s);
  }

  @Test
  void confirmsThresholdAndClosesExactlyOneIncidentAndQueuesNotifications() {
    enableEmail();
    var w = website();
    w.setFailureThreshold(3);
    websites.save(w);
    status.set(503);
    service.pingWebsite(w);
    service.pingWebsite(w);
    assertEquals(0, incidents.count());
    assertEquals(0, alerts.count());
    service.pingWebsite(w);
    service.pingWebsite(w);
    assertEquals(1, incidents.count());
    assertEquals("OPEN", incidents.findAll().get(0).getStatus());
    assertEquals(1, deliveries.count());
    status.set(200);
    service.pingWebsite(w);
    service.pingWebsite(w);
    assertEquals("RESOLVED", incidents.findAll().get(0).getStatus());
    assertEquals(2, alerts.count());
    assertEquals(2, deliveries.count());
  }

  @Test
  void scheduledChecksHonorDueTimeUnderConcurrency() throws Exception {
    var w = website();
    var executor = Executors.newFixedThreadPool(4);
    try {
      var futures = new ArrayList<Future<UptimeCheckLog>>();
      for (int i = 0; i < 4; i++)
        futures.add(executor.submit(() -> service.check(w.getId(), "SCHEDULED")));
      int results = 0;
      for (var f : futures) if (f.get(10, TimeUnit.SECONDS) != null) results++;
      assertEquals(1, results);
      assertEquals(1, requests.get());
      assertEquals("SCHEDULED", logs.findAll().get(0).getTriggerType());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void schedulerDispatchesDueWebsitesAndSkipsPausedOnes() throws Exception {
    var w = website();
    var paused = website();
    paused.setIsActive(false);
    websites.save(paused);
    var jobs = new MonitoringJobs(websites, service);
    try {
      jobs.scan();
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (logs.count() == 0 && System.nanoTime() < end) Thread.sleep(20);
      assertEquals(1, logs.count());
      jobs.scan();
      assertEquals(1, requests.get());
    } finally {
      jobs.close();
    }
  }

  @Test
  void maintenanceSuppressesChecksAndAlerts() throws Exception {
    var w = website();
    websites.save(w);
    var m = new MaintenanceWindow();
    m.setWebsiteId(w.getId());
    m.setTitle("Nâng cấp");
    m.setStartsAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    m.setEndsAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
    maintenance.save(m);
    assertNull(service.check(w.getId(), "SCHEDULED"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class, () -> service.pingWebsite(w));
    assertEquals(0, requests.get());
    assertEquals(0, logs.count());
    assertEquals(0, alerts.count());
  }

  @Test
  void settingsAcceptHttpCodeRangesAndValidateServerSide() throws Exception {
    var w = website();
    w.setExpectedCodes("201, 400-404");
    w.setSlowThresholdMs(1);
    websites.save(w);
    status.set(404);
    assertTrue(service.pingWebsite(w).getIsUp());
    w = fresh(w);
    w.setExpectedCodes("500-200");
    var invalid = w;
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.saveWebsite(invalid));
    mvc.perform(
            post("/api/websites")
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Bad\",\"url\":\"file:///etc/passwd\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void historyAndMetricsUseMoreThanTwentySamples() throws Exception {
    var w = website();
    var now = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
    for (int i = 0; i < 30; i++) {
      var l =
          new UptimeCheckLog(
              w, i == 0 ? 500 : 200, (long) (i + 1), i != 0, i == 0 ? "error" : null);
      l.setCheckedAt(now.plusMinutes(i));
      logs.save(l);
    }
    mvc.perform(
            get("/api/websites/" + w.getId() + "/history").param("page", "2").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(30))
        .andExpect(jsonPath("$.content.length()").value(10));
    mvc.perform(get("/api/websites/" + w.getId() + "/metrics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sampleCount").value(30))
        .andExpect(jsonPath("$.p95ResponseTimeMs").value(29));
    mvc.perform(get("/api/websites/" + w.getId() + "/history").param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void downtimeExcludesOverlappingMaintenanceOnlyOnce() {
    var w = website();
    var start = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
    var i = new Incident();
    i.setWebsiteId(w.getId());
    i.setFirstFailureAt(start);
    i.setDetectedAt(start);
    i.setResolvedAt(start.plusHours(1));
    i.setStatus("RESOLVED");
    incidents.save(i);
    for (int offset : new int[] {10, 20}) {
      var m = new MaintenanceWindow();
      m.setWebsiteId(w.getId());
      m.setTitle("Bảo trì");
      m.setStartsAt(start.plusMinutes(offset));
      m.setEndsAt(start.plusMinutes(offset + 20));
      maintenance.save(m);
    }
    var data =
        reports.metrics(w.getId(), new ReportingService.Range(start, start.plusHours(1)), "5m");
    assertEquals(1800L, data.get("downtimeSeconds"));
  }

  @Test
  void csvPreviewAndImportAreAtomicAndHandleQuotesAndDuplicates() {
    String text =
        "\uFEFFname,url,groupName,environment,tags\r\n"
            + "\"Site, A\",https://example.com,Project,production,\"web,api\"\r\n";
    assertTrue(csv.preview(text).errors().isEmpty());
    assertEquals(0, websites.count());
    assertEquals(1, csv.apply(text).get("imported"));
    var w = websites.findAll().get(0);
    assertEquals("Site, A", w.getName());
    assertEquals("web,api", w.getTags());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> csv.apply(text + "Invalid,file:///tmp\n"));
    assertEquals(1, websites.count());
    assertFalse(csv.preview(text).errors().isEmpty());
  }

  @Test
  void removedPublicStatusApiDoesNotExposeWebsiteData() throws Exception {
    website();
    mvc.perform(get("/api/public/status").with(anonymous())).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/public/status")).andExpect(status().isNotFound());
    mvc.perform(get("/api/websites"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].publicVisible").doesNotExist());
  }

  @Test
  void exportUsesFullHistoryAndEscapesSpreadsheetFormulaValues() throws Exception {
    var w = website();
    w.setName("=HYPERLINK(1)");
    websites.save(w);
    var l = new UptimeCheckLog(w, 500, 12L, false, "+malicious");
    logs.save(l);
    mvc.perform(get("/api/reports/export").param("websiteId", w.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(content().string(containsString("'=HYPERLINK(1)")))
        .andExpect(content().string(containsString("'+malicious")));
  }

  @Test
  void archiveKeepsHistoryAndStopsOpenIncident() throws Exception {
    var w = website();
    status.set(500);
    service.pingWebsite(w);
    service.deleteWebsite(w.getId());
    assertEquals(1, logs.count());
    assertEquals("MONITORING_STOPPED", incidents.findAll().get(0).getStatus());
    assertTrue(service.getAllWebsites().isEmpty());
    mvc.perform(get("/api/websites/" + w.getId())).andExpect(status().isNotFound());
  }

  @Test
  void anonymousCannotReadPrivateDataAndWritesRequireCsrf() throws Exception {
    mvc.perform(get("/api/websites").with(anonymous())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/websites").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "viewer", roles = "VIEWER")
  void viewerCanReadAndLogoutButCannotModifyOrManageUsers() throws Exception {
    mvc.perform(get("/api/websites")).andExpect(status().isOk());
    mvc.perform(post("/api/websites").with(csrf()).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/users")).andExpect(status().isForbidden());
    mvc.perform(delete("/api/session").with(csrf())).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "operator", roles = "OPERATOR")
  void operatorCanConfigureMonitorButCannotChangeNotificationCredentialsOrUsers() throws Exception {
    mvc.perform(
            post("/api/websites")
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Site\",\"url\":\"https://example.com\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/notifications/settings")).andExpect(status().isForbidden());
    mvc.perform(get("/api/users")).andExpect(status().isForbidden());
  }

  @Test
  void defaultAdminUsesRequestedPasswordAndStartupPreservesExistingAccounts() throws Exception {
    users.deleteAll();
    var arguments = new org.springframework.boot.DefaultApplicationArguments(new String[0]);
    defaultAdmin.run(arguments);
    var admin = users.findByUsername("admin").orElseThrow();
    assertTrue(encoder.matches("admin123", admin.getPasswordHash()));
    assertNotEquals("admin123", admin.getPasswordHash());
    assertEquals("ADMIN", admin.getRole());
    assertTrue(admin.getEnabled());
    mvc.perform(
            post("/api/session")
                .with(anonymous())
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    defaultAdmin.run(arguments);
    assertEquals(1, users.count());
    admin.setPasswordHash(encoder.encode("ChangedPassword123"));
    users.save(admin);
    defaultAdmin.run(arguments);
    assertTrue(
        encoder.matches(
            "ChangedPassword123", users.findByUsername("admin").orElseThrow().getPasswordHash()));
    mvc.perform(get("/api/setup")).andExpect(status().isNotFound());
  }

  @Test
  void realSessionLoginLogoutAndDisabledUserRevocation() throws Exception {
    org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
    var result =
        mvc.perform(
                post("/api/session")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"username\":\"viewer\",\"password\":\"StrongPassword123\"}"))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession();
    mvc.perform(get("/api/session").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("VIEWER"));
    var viewer = users.findByUsername("viewer").orElseThrow();
    viewer.setEnabled(false);
    users.save(viewer);
    mvc.perform(get("/api/websites").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void adminCannotDisableLastAdminOrLeakPasswordHash() throws Exception {
    var admin = users.findByUsername("admin").orElseThrow();
    mvc.perform(
            put("/api/users/" + admin.getId())
                .with(csrf())
                .contentType("application/json")
                .content("{\"role\":\"VIEWER\",\"enabled\":false}"))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("passwordHash"))));
  }

  @Test
  void emailOutboxSendsOnceAndRetriesWithoutLosingMonitorData() {
    enableEmail();
    var w = website();
    status.set(500);
    service.pingWebsite(w);
    assertEquals(1, deliveries.count());
    doThrow(new MailSendException("failure")).when(sender).send(any(SimpleMailMessage.class));
    notifications.deliverPending();
    var d = deliveries.findAll().get(0);
    assertEquals("PENDING", d.getDeliveryStatus());
    assertEquals(1, d.getAttempts());
    assertEquals(1, logs.count());
    reset(sender);
    d.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
    deliveries.save(d);
    notifications.deliverPending();
    notifications.deliverPending();
    verify(sender, times(1)).send(any(SimpleMailMessage.class));
    assertEquals("SENT", deliveries.findAll().get(0).getDeliveryStatus());
  }

  @Test
  @SuppressWarnings("unchecked")
  void sslExpiryIsStoredAndWarningIsDeduplicated() throws Exception {
    enableEmail();
    var w = website();
    w.setUrl("https://example.invalid");
    websites.save(w);
    var cert = mock(X509Certificate.class);
    when(cert.getNotAfter())
        .thenReturn(Date.from(Instant.now().plus(6, java.time.temporal.ChronoUnit.DAYS)));
    var tls = mock(SSLSession.class);
    when(tls.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {cert});
    HttpResponse<Void> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.sslSession()).thenReturn(Optional.of(tls));
    doReturn(CompletableFuture.completedFuture(response))
        .when(http)
        .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    service.pingWebsite(w);
    service.pingWebsite(w);
    assertNotNull(fresh(w).getSslExpiresAt());
    assertEquals(1, deliveries.count());
  }

  @Test
  void customSlowThresholdAndTimeoutAreAppliedToRealHttpRequests() {
    var w = website();
    w.setTimeoutMs(1000);
    w.setSlowThresholdMs(10);
    websites.save(w);
    delay.set(40);
    assertTrue(service.pingWebsite(w).getIsUp());
    assertEquals("SLOW", fresh(w).getMonitoringStatus());
    w = fresh(w);
    w.setTimeoutMs(100);
    websites.save(w);
    delay.set(300);
    var timeout = service.pingWebsite(w);
    assertFalse(timeout.getIsUp());
    assertNull(timeout.getStatusCode());
    assertNotNull(timeout.getErrorMessage());
  }

  @Test
  void tlsVerificationFailuresAreRecordedAndDeduplicated() throws Exception {
    enableEmail();
    var w = website();
    w.setUrl("https://example.invalid");
    websites.save(w);
    doReturn(
            CompletableFuture.failedFuture(
                new javax.net.ssl.SSLHandshakeException("certificate expired")))
        .when(http)
        .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    assertFalse(service.pingWebsite(w).getIsUp());
    service.pingWebsite(w);
    assertNotNull(fresh(w).getSslError());
    assertEquals(2, deliveries.count());
    assertEquals(1, alerts.count());
  }

  @Test
  void transactionRollbackAlsoRemovesIncidentAndOutboxRows() {
    enableEmail();
    var w = website();
    status.set(503);
    doThrow(new IllegalStateException("storage failed")).when(alerts).save(any(WebsiteAlert.class));
    assertThrows(IllegalStateException.class, () -> service.pingWebsite(w));
    assertEquals(0, logs.count());
    assertEquals(0, incidents.count());
    assertEquals(0, deliveries.count());
    assertEquals("UNKNOWN", fresh(w).getMonitoringStatus());
  }

  @Test
  void malformedImportHeadersAndMultibyteSizeAreRejected() {
    assertFalse(csv.preview("url,url\nhttps://example.com,https://example.org").errors().isEmpty());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> csv.preview("漢".repeat(400000)));
  }

  @Test
  void dateRangesRejectInvalidOrderAndOverNinetyThreeDays() throws Exception {
    var w = website();
    mvc.perform(
            get("/api/websites/" + w.getId() + "/metrics")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-04-04T01:00:00Z"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/websites/" + w.getId() + "/history")
                .param("from", "2026-01-02T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shutdownInterruptionDoesNotCreateFalseDownIncident() throws Exception {
    var w = website();
    doReturn(CompletableFuture.failedFuture(new InterruptedException("shutdown")))
        .when(http)
        .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    try {
      assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> service.pingWebsite(w));
    } finally {
      Thread.interrupted();
    }
    assertEquals(0, logs.count());
    assertEquals(0, incidents.count());
    assertEquals(0, alerts.count());
  }
}
