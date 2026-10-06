package com.mycompany.do_an_ltm_2380600550;

import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import com.mycompany.do_an_ltm_2380600550.service.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Do_An_LTM_2380600550.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:alert-tests;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class AlertIntegrationTest {
    @Autowired private WebsiteService websiteService;
    @Autowired private AlertService alertService;
    @Autowired private WebsiteRepository websites;
    @Autowired private UptimeCheckLogRepository logs;
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private WebsiteAlertRepository alerts;

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        alerts.deleteAll();
        logs.deleteAll();
        websites.deleteAll();
        status.set(200);
        requests.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private Website website() {
        return websites.save(new Website("Website thực tế", "http://127.0.0.1:"
                + server.getAddress().getPort() + "/", 30, true));
    }

    private UptimeCheckLog check(Website website, int httpStatus) {
        status.set(httpStatus);
        return websiteService.pingWebsite(website);
    }

    @Test
    void recordsDownAndRecoveryOnlyWhenStateChanges() throws Exception {
        Website website = website();
        assertTrue(check(website, 200).getIsUp());
        assertEquals(0, alerts.count());
        UptimeCheckLog down = check(website, 503);
        assertFalse(down.getIsUp());
        check(website, 404);
        assertEquals(1, alerts.count());
        UptimeCheckLog recovery = check(website, 302);
        check(website, 200);

        var result = alertService.getAlerts(0, 8, website.getId(), null);
        assertEquals(2, result.getTotalElements());
        assertEquals(AlertEventType.RECOVERY, result.getContent().get(0).eventType());
        assertEquals(recovery.getId(), result.getContent().get(0).checkLogId());
        assertEquals(AlertEventType.DOWN, result.getContent().get(1).eventType());
        assertEquals(down.getId(), result.getContent().get(1).checkLogId());
        assertTrue(result.getContent().get(1).message().contains("HTTP 503"));
        assertEquals(5, logs.count());
        assertEquals(5, requests.get());
        mvc.perform(get("/api/alerts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].websiteName").value("Website thực tế"))
                .andExpect(jsonPath("$.content[0].eventType").value("RECOVERY"));
    }

    @Test
    void firstFailureCreatesOneDownAlert() {
        Website website = website();
        check(website, 500);
        check(website, 500);
        assertEquals(1, alerts.count());
        assertEquals(AlertEventType.DOWN, alerts.findAll().get(0).getEventType());
    }

    @Test
    void connectionFailureRecordsUsefulErrorAndRecovers() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Website website = websites.save(new Website("Kết nối lỗi", "http://127.0.0.1:" + port, 30, true));
        UptimeCheckLog failed = websiteService.pingWebsite(website);
        assertFalse(failed.getIsUp());
        assertNull(failed.getStatusCode());
        assertNotNull(failed.getErrorMessage());
        assertFalse(failed.getErrorMessage().isBlank());
        assertEquals(1, alerts.count());

        website.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        website = websites.save(website);
        check(website, 200);
        assertEquals(2, alerts.count());
    }

    @Test
    void simultaneousChecksDoNotDuplicateAlerts() throws Exception {
        Website website = website();
        status.set(503);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(1);
        List<Future<UptimeCheckLog>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    ready.await();
                    return websiteService.pingWebsite(website);
                }));
            }
            ready.countDown();
            for (var future : futures) assertFalse(future.get(15, TimeUnit.SECONDS).getIsUp());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(4, logs.count());
        assertEquals(1, alerts.count());
    }

    @Test
    void historicalTransitionsAreBackfilledBeyondTwentyLogsAndOnlyOnce() {
        Website website = website();
        LocalDateTime originalTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        for (int i = 0; i < 25; i++) {
            boolean up = i == 0 || i == 24;
            UptimeCheckLog log = new UptimeCheckLog(website, up ? 200 : 503, 10L, up, up ? null : "HTTP 503");
            log.setCheckedAt(originalTime.plusMinutes(i));
            logs.save(log);
        }
        alertService.synchronizeWebsiteHistory(website.getId());
        var before = alertService.getAlerts(0, 8, null, null).getContent();
        assertEquals(2, before.size());
        assertEquals(originalTime.plusMinutes(24), before.get(0).createdAt());
        assertEquals(originalTime.plusMinutes(1), before.get(1).createdAt());
        alertService.synchronizeWebsiteHistory(website.getId());
        assertEquals(before, alertService.getAlerts(0, 8, null, null).getContent());
        check(website, 200);
        assertEquals(2, alerts.count());
        assertEquals(20, websiteService.getLogsByWebsiteId(website.getId()).size());
        assertEquals(1, requests.get());
    }

    @Test
    void paginatesAndFiltersAlertsAndRejectsInvalidQueries() throws Exception {
        Website website = website();
        check(website, 503);
        check(website, 200);
        check(website, 503);
        check(website, 200);
        mvc.perform(get("/api/alerts").param("page", "1").param("size", "1")
                        .param("websiteId", website.getId().toString()).param("eventType", "DOWN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("DOWN"));
        mvc.perform(get("/api/alerts").param("websiteId", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/alerts").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/alerts").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/alerts").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/alerts").param("eventType", "INVALID")).andExpect(status().isBadRequest());
    }

    @Test
    void pausedWebsiteCannotBeCheckedOrGenerateAlerts() throws Exception {
        Website website = website();
        website.setIsActive(false);
        websites.save(website);
        mvc.perform(post("/api/websites/" + website.getId() + "/check")).andExpect(status().isConflict());
        assertEquals(0, requests.get());
        assertEquals(0, logs.count());
        assertEquals(0, alerts.count());
    }

    @Test
    void resultAndAlertAreRolledBackTogetherIfSavingAlertFails() {
        Website website = website();
        status.set(503);
        doThrow(new IllegalStateException("Alert storage failed")).when(alerts).save(any(WebsiteAlert.class));
        assertThrows(IllegalStateException.class, () -> websiteService.pingWebsite(website));
        assertEquals(0, logs.count());
        assertEquals(0, alerts.count());
    }

    @Test
    void newDatabaseHasEmptyAlerts() throws Exception {
        mvc.perform(get("/api/alerts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
