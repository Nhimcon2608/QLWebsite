package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.dto.AlertResponse;
import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AlertService {
  private final WebsiteAlertRepository alerts;
  private final UptimeCheckLogRepository logs;
  private final WebsiteRepository websites;
  private final IncidentRepository incidents;
  private final NotificationService notifications;

  public AlertService(
      WebsiteAlertRepository alerts,
      UptimeCheckLogRepository logs,
      WebsiteRepository websites,
      IncidentRepository incidents,
      NotificationService notifications) {
    this.alerts = alerts;
    this.logs = logs;
    this.websites = websites;
    this.incidents = incidents;
    this.notifications = notifications;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordTransition(UptimeCheckLog previous, UptimeCheckLog current) {
    record(current, true);
  }

  private void record(UptimeCheckLog current, boolean notify) {
    var w = current.getWebsite();
    boolean up = Boolean.TRUE.equals(current.getIsUp());
    String before = w.getMonitoringStatus();
    AlertEventType type = null;
    if (up) {
      w.setConsecutiveFailures(0);
      w.setFirstFailureAt(null);
      w.setMonitoringStatus(current.getResponseTimeMs() > w.getSlowThresholdMs() ? "SLOW" : "UP");
      if (before.equals("DOWN")) {
        type = AlertEventType.RECOVERY;
        incidents
            .findFirstByWebsiteIdAndStatusOrderByIdDesc(w.getId(), "OPEN")
            .ifPresent(
                i -> {
                  i.setStatus("RESOLVED");
                  i.setResolvedAt(current.getCheckedAt());
                  incidents.save(i);
                });
      }
    } else {
      w.setConsecutiveFailures(w.getConsecutiveFailures() + 1);
      if (w.getFirstFailureAt() == null) w.setFirstFailureAt(current.getCheckedAt());
      if (w.getConsecutiveFailures() >= w.getFailureThreshold()) {
        w.setMonitoringStatus("DOWN");
        if (!before.equals("DOWN")) {
          type = AlertEventType.DOWN;
          if (!incidents.existsByOpeningCheckLogId(current.getId())) {
            var i = new Incident();
            i.setWebsiteId(w.getId());
            i.setOpeningCheckLogId(current.getId());
            i.setFirstFailureAt(w.getFirstFailureAt());
            i.setDetectedAt(current.getCheckedAt());
            i.setReason(current.getErrorMessage());
            incidents.save(i);
          }
        }
      }
    }
    w.setLastCheckedAt(current.getCheckedAt());
    if (type == null || alerts.existsByCheckLogId(current.getId())) return;
    String message =
        w.getName()
            + (type == AlertEventType.RECOVERY
                ? " đã hoạt động trở lại."
                : " đang ngừng hoạt động.")
            + " "
            + (current.getStatusCode() != null
                ? "HTTP " + current.getStatusCode()
                : current.getErrorMessage());
    alerts.save(new WebsiteAlert(current, type, message));
    if (notify) {
      String details =
          message + "\nURL: " + w.getUrl() + "\nThời điểm: " + current.getCheckedAt() + " UTC";
      if (type == AlertEventType.RECOVERY) {
        var resolved =
            incidents.findByWebsiteIdOrderByIdDesc(w.getId()).stream()
                .filter(i -> i.getResolvedAt() != null)
                .findFirst();
        if (resolved.isPresent())
          details +=
              "\nGián đoạn: "
                  + java.time.Duration.between(
                          resolved.get().getFirstFailureAt(), current.getCheckedAt())
                      .toSeconds()
                  + " giây";
      }
      notifications.enqueue("[QLWebsite] " + type + " · " + w.getName(), details);
    }
  }

  @Transactional
  public void synchronizeWebsiteHistory(Long websiteId) {
    var w = websites.findByIdForUpdate(websiteId).orElse(null);
    if (w == null || w.getLastCheckedAt() != null) return;
    for (var log : logs.findByWebsiteIdOrderByIdAsc(websiteId)) record(log, false);
  }

  @Transactional(readOnly = true)
  public Page<AlertResponse> getAlerts(
      int page, int size, Long websiteId, AlertEventType eventType) {
    return alerts
        .search(
            websiteId,
            eventType,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
        .map(AlertResponse::from);
  }
}
