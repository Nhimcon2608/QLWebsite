package com.mycompany.do_an_ltm_2380600550;

import static org.junit.jupiter.api.Assertions.*;

import com.mycompany.do_an_ltm_2380600550.repository.*;
import com.mycompany.do_an_ltm_2380600550.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = Do_An_LTM_2380600550.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:legacy;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=update",
      "spring.jpa.show-sql=false",
      "app.jobs.enabled=false",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:legacy-schema.sql",
      "logging.level.org.hibernate.SQL=OFF"
    })
class LegacyUpgradeTest {
  @Autowired WebsiteRepository websites;
  @Autowired UptimeCheckLogRepository logs;
  @Autowired WebsiteAlertRepository alerts;
  @Autowired IncidentRepository incidents;
  @Autowired NotificationDeliveryRepository deliveries;
  @Autowired AlertService history;
  @Autowired AppUserRepository users;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;

  @Test
  void upgradesExistingSchemaAndBackfillsStateWithoutResendingNotifications() {
    var admin = users.findByUsername("admin").orElseThrow();
    assertEquals("ADMIN", admin.getRole());
    assertTrue(passwords.matches("admin123", admin.getPasswordHash()));
    var w = websites.findById(1L).orElseThrow();
    assertEquals("Legacy service", w.getName());
    assertEquals(10000, w.getTimeoutMs());
    assertEquals(1, w.getFailureThreshold());
    assertEquals("200-399", w.getExpectedCodes());
    assertEquals("HTTP", w.getCheckType());
    assertEquals("OFF", w.getKeywordMode());
    assertFalse(w.getJsonEnabled());
    assertEquals("A", w.getDnsRecordType());
    assertEquals(443, w.getTcpPort());
    assertEquals("HTTP", logs.findAll().get(0).getCheckType());
    assertEquals("UP", w.getMonitoringStatus());
    assertNotNull(w.getLastCheckedAt());
    assertEquals(3, logs.count());
    assertEquals("MANUAL", logs.findAll().get(0).getTriggerType());
    assertEquals(2, alerts.count());
    assertEquals(1, incidents.count());
    assertEquals("RESOLVED", incidents.findAll().get(0).getStatus());
    assertEquals(0, deliveries.count());
    history.synchronizeWebsiteHistory(1L);
    assertEquals(2, alerts.count());
    assertEquals(1, incidents.count());
    assertEquals(0, deliveries.count());
  }
}
