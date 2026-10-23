package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportingService {
  private final UptimeCheckLogRepository logs;
  private final IncidentRepository incidents;
  private final MaintenanceWindowRepository maintenance;
  private final WebsiteService websites;

  public ReportingService(
      UptimeCheckLogRepository logs,
      IncidentRepository incidents,
      MaintenanceWindowRepository maintenance,
      WebsiteService websites) {
    this.logs = logs;
    this.incidents = incidents;
    this.maintenance = maintenance;
    this.websites = websites;
  }

  public record Range(LocalDateTime from, LocalDateTime to) {}

  public Range range(String from, String to) {
    try {
      var end = to == null ? LocalDateTime.now(java.time.ZoneOffset.UTC) : parse(to);
      var start = from == null ? end.minusDays(1) : parse(from);
      if (!start.isBefore(end) || Duration.between(start, end).compareTo(Duration.ofDays(93)) > 0)
        throw new IllegalArgumentException();
      return new Range(start, end);
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Khoảng thời gian phải hợp lệ, tối đa 93 ngày; dùng ISO-8601 có múi giờ.");
    }
  }

  public static LocalDateTime parse(String value) {
    return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
  }

  public static String iso(LocalDateTime t) {
    return t == null ? null : t.atOffset(ZoneOffset.UTC).toString();
  }

  public static Map<String, Object> log(UptimeCheckLog l) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", l.getId().toString());
    m.put("websiteId", l.getWebsite().getId().toString());
    m.put("completedAt", iso(l.getCheckedAt()));
    m.put("success", l.getIsUp());
    m.put("httpCode", l.getStatusCode());
    m.put(
        "responseTimeMs",
        (l.getStatusCode() != null || Boolean.TRUE.equals(l.getIsUp()))
            ? l.getResponseTimeMs()
            : null);
    m.put("elapsedMs", l.getResponseTimeMs());
    m.put("errorType", l.getErrorType());
    m.put("checkType", l.getCheckType());
    m.put("checkDetails", l.getCheckDetails());
    m.put("errorMessage", l.getErrorMessage());
    m.put("triggerType", l.getTriggerType());
    return m;
  }

  public List<UptimeCheckLog> samples(Long id, Range range) {
    var page =
        logs.history(
            id,
            range.from(),
            range.to(),
            null,
            PageRequest.of(0, 100001, Sort.by("checkedAt").ascending()));
    if (page.getTotalElements() > 100000)
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY, "Quá 100.000 mẫu. Hãy chọn khoảng thời gian ngắn hơn.");
    return page.getContent();
  }

  public Map<String, Object> metrics(Long id, Range range, String bucket) {
    websites.require(id);
    var samples = samples(id, range);
    int seconds =
        switch (bucket == null ? "30m" : bucket) {
          case "5m" -> 300;
          case "1h" -> 3600;
          case "1d" -> 86400;
          case "30m" -> 1800;
          default ->
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, "Độ chia biểu đồ không hợp lệ.");
        };
    long count = Duration.between(range.from(), range.to()).toSeconds() / seconds + 1;
    if (count > 2000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn độ chia biểu đồ lớn hơn.");
    var responses =
        samples.stream()
            .filter(l -> l.getStatusCode() != null || Boolean.TRUE.equals(l.getIsUp()))
            .mapToLong(UptimeCheckLog::getResponseTimeMs)
            .sorted()
            .toArray();
    long success = samples.stream().filter(l -> Boolean.TRUE.equals(l.getIsUp())).count();
    List<Map<String, Object>> points = new ArrayList<>();
    int cursor = 0;
    for (var start = range.from(); start.isBefore(range.to()); start = start.plusSeconds(seconds)) {
      var end = start.plusSeconds(seconds);
      long sum = 0, n = 0, fail = 0, total = 0;
      while (cursor < samples.size() && samples.get(cursor).getCheckedAt().isBefore(end)) {
        var l = samples.get(cursor++);
        total++;
        if (!Boolean.TRUE.equals(l.getIsUp())) fail++;
        if (l.getStatusCode() != null || Boolean.TRUE.equals(l.getIsUp())) {
          sum += l.getResponseTimeMs();
          n++;
        }
      }
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("at", iso(start));
      p.put("responseTimeMs", n == 0 ? null : Math.round((double) sum / n));
      p.put("sampleCount", total);
      p.put("failureCount", fail);
      points.add(p);
    }
    var allIncidents = incidents.findByWebsiteIdOrderByIdDesc(id);
    long downtime = 0, incidentCount = 0;
    var windows = maintenance.findByWebsiteIdOrderByStartsAtDesc(id);
    for (var i : allIncidents) {
      var begin =
          i.getFirstFailureAt().isAfter(range.from()) ? i.getFirstFailureAt() : range.from();
      var finish =
          i.getResolvedAt() == null
              ? LocalDateTime.now(java.time.ZoneOffset.UTC)
              : i.getResolvedAt();
      if (finish.isAfter(range.to())) finish = range.to();
      if (!begin.isBefore(finish)) continue;
      incidentCount++;
      long duration = Duration.between(begin, finish).toSeconds();
      // Merge overlapping maintenance intervals before subtracting them.
      final var low = begin;
      final var high = finish;
      var overlaps =
          windows.stream()
              .filter(w -> w.getStartsAt().isBefore(high) && w.getEndsAt().isAfter(low))
              .sorted(Comparator.comparing(MaintenanceWindow::getStartsAt))
              .toList();
      LocalDateTime mergedStart = null, mergedEnd = null;
      for (var w : overlaps) {
        var a = w.getStartsAt().isBefore(begin) ? begin : w.getStartsAt();
        var b = w.getEndsAt().isAfter(finish) ? finish : w.getEndsAt();
        if (mergedStart == null) {
          mergedStart = a;
          mergedEnd = b;
        } else if (!a.isAfter(mergedEnd)) {
          if (b.isAfter(mergedEnd)) mergedEnd = b;
        } else {
          duration -= Duration.between(mergedStart, mergedEnd).toSeconds();
          mergedStart = a;
          mergedEnd = b;
        }
      }
      if (mergedStart != null) duration -= Duration.between(mergedStart, mergedEnd).toSeconds();
      downtime += Math.max(0, duration);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("from", iso(range.from()));
    result.put("to", iso(range.to()));
    result.put("sampleCount", samples.size());
    result.put("successCount", success);
    result.put("successRate", samples.isEmpty() ? null : success * 100.0 / samples.size());
    result.put(
        "averageResponseTimeMs",
        responses.length == 0 ? null : Math.round(Arrays.stream(responses).average().orElse(0)));
    result.put(
        "p95ResponseTimeMs",
        responses.length == 0 ? null : responses[(int) Math.ceil(responses.length * .95) - 1]);
    result.put("incidentCount", incidentCount);
    result.put("downtimeSeconds", downtime);
    result.put("points", points);
    return result;
  }
}
