package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import java.net.URI;
import java.net.http.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebsiteService {
  private final WebsiteRepository websites;
  private final UptimeCheckLogRepository logs;
  private final AlertService alerts;
  private final MaintenanceWindowRepository maintenance;
  private final IncidentRepository incidents;
  private final NotificationService notifications;
  private final HttpClient http;
  private final ContentChecks contentChecks;
  private final NetworkChecks networkChecks;

  public WebsiteService(
      WebsiteRepository websites,
      UptimeCheckLogRepository logs,
      AlertService alerts,
      MaintenanceWindowRepository maintenance,
      IncidentRepository incidents,
      NotificationService notifications,
      HttpClient monitorHttpClient,
      ContentChecks contentChecks,
      NetworkChecks networkChecks) {
    this.websites = websites;
    this.logs = logs;
    this.alerts = alerts;
    this.maintenance = maintenance;
    this.incidents = incidents;
    this.notifications = notifications;
    this.http = monitorHttpClient;
    this.contentChecks = contentChecks;
    this.networkChecks = networkChecks;
  }

  public List<Website> getAllWebsites() {
    return websites.findByArchivedAtIsNullOrderByIdDesc().stream().map(this::decorate).toList();
  }

  public Optional<Website> getWebsiteById(Long id) {
    return websites.findById(id).filter(w -> w.getArchivedAt() == null).map(this::decorate);
  }

  private Website decorate(Website w) {
    w.setMaintenanceActive(inMaintenance(w.getId(), LocalDateTime.now(java.time.ZoneOffset.UTC)));
    return w;
  }

  public Website require(Long id) {
    return getWebsiteById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website không tồn tại."));
  }

  public Website saveWebsite(Website w) {
    validate(w);
    return websites.save(w);
  }

  @Transactional
  public Website create(Website input) {
    var w = new Website();
    copyConfiguration(input, w);
    validate(w);
    checkDuplicate(w);
    return websites.save(w);
  }

  @Transactional
  public Website update(Long id, Website input) {
    var w =
        websites
            .findByIdForUpdate(id)
            .filter(x -> x.getArchivedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    boolean wasActive = Boolean.TRUE.equals(w.getIsActive());
    String oldUrl = w.getUrl();
    String oldTarget = target(w);
    copyConfiguration(input, w);
    validate(w);
    checkDuplicate(w);
    if (!Boolean.TRUE.equals(w.getIsActive()) || !Objects.equals(oldTarget, target(w)))
      stopIncident(w);
    if (!Objects.equals(oldUrl, w.getUrl()) || !w.getCheckType().equals("HTTP")) {
      w.setSslCheckedAt(null);
      w.setSslExpiresAt(null);
      w.setSslError(null);
      w.setSslAlertKey(null);
    }
    if ((!wasActive && Boolean.TRUE.equals(w.getIsActive()))
        || !Objects.equals(oldTarget, target(w))) {
      w.setMonitoringStatus("UNKNOWN");
      w.setConsecutiveFailures(0);
      w.setFirstFailureAt(null);
    }
    w.setNextCheckAt(null);
    return websites.save(w);
  }

  private void copyConfiguration(Website a, Website b) {
    b.setName(a.getName());
    b.setUrl(a.getUrl());
    b.setCheckIntervalSeconds(a.getCheckIntervalSeconds());
    b.setIsActive(a.getIsActive());
    b.setTimeoutMs(a.getTimeoutMs());
    b.setSlowThresholdMs(a.getSlowThresholdMs());
    b.setFailureThreshold(a.getFailureThreshold());
    b.setExpectedCodes(a.getExpectedCodes());
    b.setDescription(a.getDescription());
    b.setGroupName(a.getGroupName());
    b.setEnvironment(a.getEnvironment());
    b.setTags(a.getTags());
    b.setCheckType(a.getCheckType());
    b.setKeywordMode(a.getKeywordMode());
    b.setKeyword(a.getKeyword());
    b.setKeywordCaseSensitive(a.getKeywordCaseSensitive());
    b.setJsonEnabled(a.getJsonEnabled());
    b.setJsonPointer(a.getJsonPointer());
    b.setJsonComparison(a.getJsonComparison());
    b.setJsonExpected(a.getJsonExpected());
    b.setDnsRecordType(a.getDnsRecordType());
    b.setDnsExpected(a.getDnsExpected());
    b.setTcpPort(a.getTcpPort());
    b.setSslWarnDays(a.getSslWarnDays());
  }

  private String target(Website w) {
    return w.getUrl()
        + "|"
        + w.getCheckType()
        + "|"
        + (w.getCheckType().equals("TCP")
            ? w.getTcpPort()
            : w.getCheckType().equals("DNS") ? w.getDnsRecordType() : "");
  }

  private void checkDuplicate(Website w) {
    if (getAllWebsites().stream()
        .anyMatch(x -> !Objects.equals(x.getId(), w.getId()) && target(x).equals(target(w))))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Mục tiêu và kiểu kiểm tra này đã có trong danh sách.");
  }

  public void validate(Website w) {
    contentChecks.validate(w);
    if (w.getName() == null || w.getName().isBlank() || w.getName().length() > 100)
      bad("Tên website phải từ 1 đến 100 ký tự.");
    try {
      var uri = URI.create(w.getUrl());
      if (!Set.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getFragment() != null
          || w.getUrl().length() > 500) throw new IllegalArgumentException();
    } catch (Exception e) {
      bad("URL HTTP/HTTPS không hợp lệ hoặc chứa tài khoản/fragment.");
    }
    if (w.getCheckIntervalSeconds() == null
        || w.getCheckIntervalSeconds() < 10
        || w.getCheckIntervalSeconds() > 86400) bad("Chu kỳ phải từ 10 đến 86400 giây.");
    if (w.getIsActive() == null) bad("Trạng thái giám sát không hợp lệ.");
    if (w.getTimeoutMs() < 100
        || w.getTimeoutMs() > 60000
        || w.getSlowThresholdMs() < 1
        || w.getSlowThresholdMs() >= w.getTimeoutMs())
      bad("Timeout 100–60000 ms; ngưỡng chậm phải nhỏ hơn timeout.");
    if (w.getFailureThreshold() < 1 || w.getFailureThreshold() > 10)
      bad("Ngưỡng lỗi phải từ 1 đến 10.");
    if (w.getDescription().length() > 1000
        || w.getGroupName().length() > 100
        || w.getEnvironment().length() > 100
        || w.getTags().length() > 1000)
      bad("Mô tả/tag quá dài (tối đa 1000 ký tự); nhóm/môi trường tối đa 100 ký tự.");
    if (w.getSslWarnDays() < 1 || w.getSslWarnDays() > 90) bad("Ngưỡng SSL phải từ 1 đến 90 ngày.");
    try {
      if (w.getExpectedCodes().isBlank() || w.getExpectedCodes().length() > 200)
        throw new IllegalArgumentException();
      for (String part : w.getExpectedCodes().split(",", -1)) {
        if (!part.trim().matches("[1-5][0-9]{2}(?:-[1-5][0-9]{2})?"))
          throw new IllegalArgumentException();
        String[] range = part.trim().split("-");
        if (range.length == 2 && Integer.parseInt(range[0]) > Integer.parseInt(range[1]))
          throw new IllegalArgumentException();
      }
    } catch (Exception e) {
      bad("Mã HTTP chấp nhận không hợp lệ. Ví dụ: 200-299, 301.");
    }
    w.setName(w.getName().trim());
    w.setUrl(w.getUrl().trim());
  }

  private void bad(String message) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  @Transactional
  public void deleteWebsite(Long id) {
    var w =
        websites
            .findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    stopIncident(w);
    w.setIsActive(false);
    w.setArchivedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
  }

  private void stopIncident(Website w) {
    incidents
        .findFirstByWebsiteIdAndStatusOrderByIdDesc(w.getId(), "OPEN")
        .ifPresent(
            i -> {
              i.setStatus("MONITORING_STOPPED");
              i.setResolvedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
              incidents.save(i);
            });
    w.setConsecutiveFailures(0);
    w.setFirstFailureAt(null);
    w.setMonitoringStatus("UNKNOWN");
  }

  public boolean inMaintenance(Long id, LocalDateTime now) {
    return maintenance.existsByWebsiteIdAndStartsAtLessThanEqualAndEndsAtGreaterThan(id, now, now);
  }

  @Transactional
  public UptimeCheckLog pingWebsite(Website w) {
    return check(w.getId(), "MANUAL");
  }

  @Transactional
  public UptimeCheckLog check(Long id, String trigger) {
    var w =
        websites
            .findByIdForUpdate(id)
            .filter(x -> x.getArchivedAt() == null)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website không tồn tại."));
    var now = LocalDateTime.now(java.time.ZoneOffset.UTC);
    boolean scheduled = trigger.equals("SCHEDULED");
    if (!Boolean.TRUE.equals(w.getIsActive()) || inMaintenance(id, now)) {
      if (scheduled) return null;
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Website đang tạm dừng hoặc trong lịch bảo trì.");
    }
    if (scheduled && w.getNextCheckAt() != null && now.isBefore(w.getNextCheckAt())) return null;
    var previous = logs.findFirstByWebsiteIdOrderByIdDesc(id).orElse(null);
    long start = System.nanoTime();
    Integer code = null;
    boolean up = false;
    String error = null;
    String errorType = null;
    String details = null;
    try {
      if (!w.getCheckType().equals("HTTP")) {
        var result = networkChecks.check(w);
        up = result.success();
        error = result.message();
        errorType = result.errorType();
        details = result.details();
      } else {
        var req =
            HttpRequest.newBuilder(URI.create(w.getUrl()))
                .GET()
                .timeout(Duration.ofMillis(w.getTimeoutMs()))
                .build();
        HttpResponse<?> response;
        if (contentChecks.enabled(w)) response = BoundedHttp.read(http, req, w.getTimeoutMs());
        else
          response =
              BoundedHttp.send(http, req, HttpResponse.BodyHandlers.discarding(), w.getTimeoutMs());
        code = response.statusCode();
        int status = code;
        up =
            Arrays.stream(w.getExpectedCodes().split(","))
                .anyMatch(
                    p -> {
                      var r = p.trim().split("-");
                      return status >= Integer.parseInt(r[0])
                          && status <= Integer.parseInt(r[r.length - 1]);
                    });
        if (!up) {
          error = "HTTP " + code;
          errorType = "HTTP_ERROR";
        } else if (contentChecks.enabled(w)) {
          var charset = java.nio.charset.StandardCharsets.UTF_8;
          var contentType = response.headers().firstValue("Content-Type").orElse("");
          var matcher =
              java.util.regex.Pattern.compile(
                      "charset=[\"']?([^;\\s\"']+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                  .matcher(contentType);
          if (matcher.find()) {
            try {
              charset = java.nio.charset.Charset.forName(matcher.group(1));
            } catch (Exception ignored) {
            }
          }
          var result = contentChecks.check(w, new String((byte[]) response.body(), charset));
          errorType = result.errorType();
          up = errorType == null;
          if (up) details = result.message();
          else error = result.message();
        }
        if (response.sslSession().isPresent()) {
          var cert = (X509Certificate) response.sslSession().get().getPeerCertificates()[0];
          w.setSslExpiresAt(
              LocalDateTime.ofInstant(cert.getNotAfter().toInstant(), ZoneOffset.UTC));
          w.setSslCheckedAt(now);
          w.setSslError(null);
          long days = Duration.between(now, w.getSslExpiresAt()).toDays();
          if (days <= w.getSslWarnDays()) {
            int threshold = days <= 0 ? 0 : days <= 7 ? 7 : days <= 14 ? 14 : w.getSslWarnDays();
            String key = w.getSslExpiresAt() + ":" + threshold;
            if (!key.equals(w.getSslAlertKey())) {
              notifications.enqueue(
                  "[QLWebsite] SSL · " + w.getName(),
                  w.getUrl()
                      + "\nChứng chỉ hết hạn: "
                      + w.getSslExpiresAt()
                      + " UTC\nCòn "
                      + days
                      + " ngày.");
              w.setSslAlertKey(key);
            }
          } else w.setSslAlertKey(null);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Kiểm tra bị gián đoạn; chưa xác nhận trạng thái.");
    } catch (Exception e) {
      up = false;
      errorType =
          e.toString().contains("BODY_TOO_LARGE")
              ? "BODY_TOO_LARGE"
              : e instanceof HttpTimeoutException ? "TIMEOUT" : "CONNECTION_ERROR";
      error =
          e.getMessage() != null && !e.getMessage().isBlank()
              ? e.getMessage()
              : e.getClass().getSimpleName() + ": Không thể kết nối.";
      if (e instanceof javax.net.ssl.SSLException) {
        w.setSslCheckedAt(now);
        String tlsError = "Không xác thực được chứng chỉ TLS. " + error;
        w.setSslError(tlsError.substring(0, Math.min(tlsError.length(), 1000)));
        if (!"TLS_ERROR".equals(w.getSslAlertKey())) {
          notifications.enqueue(
              "[QLWebsite] SSL không hợp lệ · " + w.getName(), w.getUrl() + "\n" + w.getSslError());
          w.setSslAlertKey("TLS_ERROR");
        }
      }
    }
    if (error != null && error.length() > 1000) error = error.substring(0, 1000);
    var log =
        new UptimeCheckLog(
            w, code, Duration.ofNanos(System.nanoTime() - start).toMillis(), up, error);
    log.setTriggerType(trigger);
    log.setCheckType(w.getCheckType());
    log.setErrorType(errorType);
    log.setCheckDetails(
        details == null ? null : details.substring(0, Math.min(details.length(), 2000)));
    log = logs.save(log);
    // A maintenance window may begin while the HTTP request is in flight.
    if (!inMaintenance(id, log.getCheckedAt())) alerts.recordTransition(previous, log);
    w.setNextCheckAt(log.getCheckedAt().plusSeconds(w.getCheckIntervalSeconds()));
    return log;
  }

  public List<UptimeCheckLog> getLogsByWebsiteId(Long id) {
    return logs.findTop20ByWebsiteIdOrderByIdDesc(id);
  }
}
