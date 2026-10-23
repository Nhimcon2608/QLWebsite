package com.mycompany.do_an_ltm_2380600550.controller;

import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import com.mycompany.do_an_ltm_2380600550.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class MonitoringController {
  private final WebsiteService websites;
  private final WebsiteRepository websiteRepo;
  private final UptimeCheckLogRepository logs;
  private final IncidentRepository incidents;
  private final MaintenanceWindowRepository maintenance;
  private final ReportingService reports;
  private final CsvImportService csv;

  public MonitoringController(
      WebsiteService websites,
      WebsiteRepository websiteRepo,
      UptimeCheckLogRepository logs,
      IncidentRepository incidents,
      MaintenanceWindowRepository maintenance,
      ReportingService reports,
      CsvImportService csv) {
    this.websites = websites;
    this.websiteRepo = websiteRepo;
    this.logs = logs;
    this.incidents = incidents;
    this.maintenance = maintenance;
    this.reports = reports;
    this.csv = csv;
  }

  @GetMapping("/websites/{id}/history")
  public Page<Map<String, Object>> history(
      @PathVariable Long id,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "8") int size,
      @RequestParam(required = false) String result) {
    websites.require(id);
    var range = reports.range(from, to);
    page(page, size);
    if (result != null && !result.isEmpty() && !Set.of("SUCCESS", "FAILURE").contains(result))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc kết quả không hợp lệ.");
    return logs.history(
            id,
            range.from(),
            range.to(),
            result == null || result.isEmpty() ? null : result.equals("SUCCESS"),
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("checkedAt"), Sort.Order.desc("id"))))
        .map(ReportingService::log);
  }

  @GetMapping("/websites/{id}/metrics")
  public Map<String, Object> metrics(
      @PathVariable Long id,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String bucket) {
    return reports.metrics(id, reports.range(from, to), bucket);
  }

  @GetMapping("/websites/{id}/incidents")
  public Page<Incident> incidents(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "5") int size) {
    websites.require(id);
    page(page, size);
    return incidents.findByWebsiteIdOrderByIdDesc(id, PageRequest.of(page, size));
  }

  @PatchMapping("/incidents/{id}")
  @Transactional
  public Incident note(@PathVariable Long id, @RequestBody Map<String, String> body) {
    var i =
        incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String text = body.getOrDefault("notes", "");
    if (text.length() > 4000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú tối đa 4000 ký tự.");
    i.setNotes(text);
    return incidents.save(i);
  }

  @GetMapping("/websites/{id}/maintenance")
  public List<MaintenanceWindow> maintenance(@PathVariable Long id) {
    websites.require(id);
    return maintenance.findByWebsiteIdOrderByStartsAtDesc(id);
  }

  @PostMapping("/websites/{id}/maintenance")
  public MaintenanceWindow addMaintenance(
      @PathVariable Long id, @RequestBody MaintenanceInput input) {
    websites.require(id);
    try {
      var a = ReportingService.parse(input.startsAt());
      var b = ReportingService.parse(input.endsAt());
      if (!a.isBefore(b)
          || Duration.between(a, b).compareTo(Duration.ofDays(93)) > 0
          || input.title() == null
          || input.title().isBlank()
          || input.title().length() > 200) throw new IllegalArgumentException();
      var w = new MaintenanceWindow();
      w.setWebsiteId(id);
      w.setTitle(input.title());
      w.setStartsAt(a);
      w.setEndsAt(b);
      return maintenance.save(w);
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Tên và khoảng thời gian bảo trì không hợp lệ (tối đa 93 ngày).");
    }
  }

  @DeleteMapping("/websites/{id}/maintenance/{windowId}")
  public void deleteMaintenance(@PathVariable Long id, @PathVariable Long windowId) {
    websites.require(id);
    var w =
        maintenance
            .findById(windowId)
            .filter(x -> x.getWebsiteId().equals(id))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    maintenance.delete(w);
  }

  @GetMapping("/dashboard/summary")
  public Map<String, Object> summary(
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    var range = reports.range(from, to);
    var all = websites.getAllWebsites();
    long active = 0,
        up = 0,
        down = 0,
        slow = 0,
        unknown = 0,
        stale = 0,
        maint = 0,
        total = 0,
        success = 0;
    var now = LocalDateTime.now(java.time.ZoneOffset.UTC);
    for (var w : all) {
      if (!Boolean.TRUE.equals(w.getIsActive())) continue;
      active++;
      if (websites.inMaintenance(w.getId(), now)) {
        maint++;
        continue;
      }
      if (w.getLastCheckedAt() != null
          && w.getLastCheckedAt()
              .plusSeconds(w.getCheckIntervalSeconds() * 2L)
              .plusNanos(w.getTimeoutMs() * 1000000L)
              .isBefore(now)) {
        stale++;
        unknown++;
      } else
        switch (w.getMonitoringStatus()) {
          case "UP" -> up++;
          case "DOWN" -> down++;
          case "SLOW" -> slow++;
          default -> unknown++;
        }
      total +=
          logs.history(w.getId(), range.from(), range.to(), null, PageRequest.of(0, 1))
              .getTotalElements();
      success +=
          logs.history(w.getId(), range.from(), range.to(), true, PageRequest.of(0, 1))
              .getTotalElements();
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("total", all.size());
    m.put("active", active);
    m.put("paused", all.size() - active);
    m.put("up", up);
    m.put("down", down);
    m.put("slow", slow);
    m.put("unknown", unknown);
    m.put("stale", stale);
    m.put("maintenance", maint);
    m.put("archived", websiteRepo.count() - all.size());
    m.put("sampleCount", total);
    m.put("successRate", total == 0 ? null : success * 100.0 / total);
    return m;
  }

  @PostMapping("/websites/import/preview")
  public CsvImportService.Preview preview(@RequestBody CsvInput input) {
    return csv.preview(input.csv());
  }

  @PostMapping("/websites/import")
  public Map<String, Object> apply(@RequestBody CsvInput input) {
    return csv.apply(input.csv());
  }

  @GetMapping("/reports/export")
  public ResponseEntity<byte[]> export(
      @RequestParam Long websiteId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to)
      throws IOException {
    var website = websites.require(websiteId);
    var range = reports.range(from, to);
    var metrics = reports.metrics(websiteId, range, "1d");
    var text = new StringWriter();
    text.write('\uFEFF');
    try (var printer = new CSVPrinter(text, CSVFormat.DEFAULT)) {
      printer.printRecord(
          "website",
          "from_utc",
          "to_utc",
          "sample_count",
          "success_rate_percent",
          "average_ms",
          "p95_ms",
          "incident_count",
          "downtime_seconds");
      printer.printRecord(
          safe(website.getName()),
          ReportingService.iso(range.from()),
          ReportingService.iso(range.to()),
          metrics.get("sampleCount"),
          metrics.get("successRate"),
          metrics.get("averageResponseTimeMs"),
          metrics.get("p95ResponseTimeMs"),
          metrics.get("incidentCount"),
          metrics.get("downtimeSeconds"));
      printer.println();
      printer.printRecord(
          "checked_at_utc",
          "success",
          "http_code",
          "response_ms",
          "trigger",
          "error",
          "check_type",
          "error_type",
          "details");
      for (var l : reports.samples(websiteId, range))
        printer.printRecord(
            ReportingService.iso(l.getCheckedAt()),
            l.getIsUp(),
            l.getStatusCode(),
            l.getResponseTimeMs(),
            l.getTriggerType(),
            safe(l.getErrorMessage()),
            l.getCheckType(),
            l.getErrorType(),
            safe(l.getCheckDetails()));
    }
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=website-" + websiteId + "-report.csv")
        .body(text.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String safe(String value) {
    if (value == null) return "";
    String trimmed = value.stripLeading();
    return !trimmed.isEmpty() && "=+@-".indexOf(trimmed.charAt(0)) >= 0 ? "'" + value : value;
  }

  private void page(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Trang >=0, kích thước từ 1 đến 100.");
  }

  public record CsvInput(String csv) {}

  public record MaintenanceInput(String title, String startsAt, String endsAt) {}
}
