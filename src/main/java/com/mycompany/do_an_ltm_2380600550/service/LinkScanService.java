package com.mycompany.do_an_ltm_2380600550.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.do_an_ltm_2380600550.entity.LinkScan;
import com.mycompany.do_an_ltm_2380600550.repository.LinkScanRepository;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LinkScanService implements ApplicationRunner {
  private final LinkScanRepository scans;
  private final WebsiteService websites;
  private final ObjectMapper json;
  private final HttpClient http;
  private final Set<Long> running = ConcurrentHashMap.newKeySet();
  private final ExecutorService workers =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(4),
          r -> {
            var t = new Thread(r, "link-scan");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  public LinkScanService(
      LinkScanRepository scans,
      WebsiteService websites,
      ObjectMapper json,
      HttpClient monitorHttpClient) {
    this.scans = scans;
    this.websites = websites;
    this.json = json;
    this.http = monitorHttpClient;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (var scan : scans.findByStatusIn(List.of("QUEUED", "RUNNING"))) {
      scan.setStatus("INTERRUPTED");
      scan.setMessage("Lần quét bị gián đoạn khi máy chủ khởi động lại. Có thể chạy lại.");
      scan.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
      scans.save(scan);
    }
  }

  public List<LinkScan> list(Long id) {
    websites.require(id);
    return scans.findTop10ByWebsiteIdOrderByIdDesc(id);
  }

  public Map<String, Object> report(Long id, Long scanId) {
    websites.require(id);
    var scan =
        scans
            .findById(scanId)
            .filter(s -> s.getWebsiteId().equals(id))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    try {
      return Map.of(
          "scan",
          scan,
          "results",
          json.readValue(scan.getResultsJson(), new TypeReference<List<Map<String, Object>>>() {}));
    } catch (Exception ex) {
      throw new IllegalStateException("Không đọc được báo cáo quét.", ex);
    }
  }

  public LinkScan start(Long id, int maxPages, int maxLinks, boolean external) {
    var w = websites.require(id);
    if (maxPages < 1 || maxPages > 20 || maxLinks < 1 || maxLinks > 200 || maxPages > maxLinks)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Giới hạn: 1–20 trang HTML, 1–200 liên kết; số trang không vượt số liên kết.");
    if (!running.add(id))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Website này đang được quét. Vui lòng chờ.");
    var scan = new LinkScan();
    scan.setWebsiteId(id);
    scan.setTargetUrl(w.getUrl());
    scan.setMaxPages(maxPages);
    scan.setMaxLinks(maxLinks);
    scan.setExternal(external);
    try {
      var saved = scans.save(scan);
      workers.execute(() -> execute(saved.getId(), Math.min(w.getTimeoutMs(), 5000)));
      return saved;
    } catch (RejectedExecutionException ex) {
      scan.setStatus("FAILED");
      scan.setMessage("Hàng đợi quét đang đầy.");
      scans.save(scan);
      running.remove(id);
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Hàng đợi quét đang đầy. Thử lại sau.");
    } catch (RuntimeException ex) {
      running.remove(id);
      throw ex;
    }
  }

  public record LinkResult(
      String url,
      String sourceUrl,
      String finalUrl,
      Integer httpCode,
      String status,
      String message) {}

  private record Pending(URI url, String source, int depth) {}

  private record Response(URI uri, HttpResponse<byte[]> response) {}

  private Response fetch(URI uri, String method, int timeout, long deadline) throws Exception {
    var seen = new HashSet<URI>();
    for (int redirect = 0; redirect <= 5; redirect++) {
      if (!seen.add(uri)) throw new java.io.IOException("Vòng lặp chuyển hướng.");
      long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
      if (remaining <= 0) throw new HttpTimeoutException("Đã hết 90 giây quét.");
      var req =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofMillis(Math.max(1, Math.min(timeout, remaining))))
              .header("User-Agent", "QLWebsite-LinkChecker/1.0")
              .method(method, HttpRequest.BodyPublishers.noBody())
              .build();
      var response = BoundedHttp.read(http, req, Math.max(1, Math.min(timeout, remaining)));
      if (Set.of(301, 302, 303, 307, 308).contains(response.statusCode())
          && response.headers().firstValue("Location").isPresent()) {
        uri = normalize(uri.resolve(response.headers().firstValue("Location").get()));
        if (uri == null)
          throw new java.io.IOException("Đích chuyển hướng không phải HTTP/HTTPS hợp lệ.");
      } else return new Response(uri, response);
    }
    throw new java.io.IOException("Quá 5 lần chuyển hướng.");
  }

  private void execute(Long scanId, int timeout) {
    var scan = scans.findById(scanId).orElseThrow();
    var results = new ArrayList<LinkResult>();
    boolean limited = false;
    try {
      scan.setStatus("RUNNING");
      scans.save(scan);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
      var root = normalize(URI.create(scan.getTargetUrl()));
      var origins = new HashSet<String>();
      origins.add(origin(root));
      var queue = new ArrayDeque<Pending>();
      queue.add(new Pending(root, root.toString(), 0));
      var visited = new HashSet<URI>();
      visited.add(root);
      int pages = 0;
      while (!queue.isEmpty() && results.size() < scan.getMaxLinks()) {
        if (System.nanoTime() >= deadline) {
          limited = true;
          break;
        }
        var pending = queue.removeFirst();
        URI url = pending.url();
        boolean internal = origins.contains(origin(url));
        // Retrieve bodies only for pages we may crawl, HEAD for other targets.
        String method =
            internal && pages < scan.getMaxPages() && pending.depth() <= 2 ? "GET" : "HEAD";
        try {
          var fetched = fetch(url, method, timeout, deadline);
          if (method.equals("HEAD") && Set.of(405, 501).contains(fetched.response().statusCode()))
            fetched = fetch(url, "GET", timeout, deadline);
          var response = fetched.response();
          int code = response.statusCode();
          String status =
              code == 401 || code == 403 || code == 429
                  ? "REVIEW"
                  : code >= 400 ? "BROKEN" : code >= 300 ? "REVIEW" : "OK";
          results.add(
              new LinkResult(
                  url.toString(),
                  pending.source(),
                  fetched.uri().toString(),
                  code,
                  status,
                  status.equals("REVIEW")
                      ? "Cần kiểm tra quyền truy cập/giới hạn/chuyển hướng."
                      : status.equals("BROKEN") ? "HTTP " + code : null));
          if (pending.depth() == 0) origins.add(origin(fetched.uri()));
          boolean html =
              response
                  .headers()
                  .firstValue("Content-Type")
                  .orElse("")
                  .toLowerCase(Locale.ROOT)
                  .contains("text/html");
          if (code < 400
              && html
              && method.equals("GET")
              && origins.contains(origin(fetched.uri()))
              && pending.depth() <= 2
              && pages < scan.getMaxPages()) {
            pages++;
            var doc =
                Jsoup.parse(
                    new java.io.ByteArrayInputStream(response.body()),
                    null,
                    fetched.uri().toString());
            for (var anchor : doc.select("a[href],area[href]")) {
              URI link;
              try {
                link = normalize(URI.create(anchor.absUrl("href")));
              } catch (Exception ex) {
                continue;
              }
              if (link == null || !scan.getExternal() && !origins.contains(origin(link))) continue;
              if (!visited.contains(link)) {
                // Bound discovery memory as well as network requests.
                if (visited.size() >= scan.getMaxLinks()) {
                  limited = true;
                  continue;
                }
                visited.add(link);
                queue.addLast(new Pending(link, fetched.uri().toString(), pending.depth() + 1));
              }
            }
          } else if (html && internal && (pages >= scan.getMaxPages() || pending.depth() > 2))
            limited = true;
        } catch (InterruptedException ex) {
          throw ex;
        } catch (Exception ex) {
          results.add(
              new LinkResult(
                  url.toString(),
                  pending.source(),
                  null,
                  null,
                  "ERROR",
                  abbreviate(
                      ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                      500)));
        }
        scan.setPages(pages);
        progress(scan, results);
      }
      limited |= !queue.isEmpty();
      boolean rootFailed =
          results.isEmpty()
              || results.get(0).status().equals("ERROR")
              || results.get(0).status().equals("BROKEN")
              || results.get(0).status().equals("REVIEW");
      scan.setStatus(rootFailed ? "FAILED" : limited ? "PARTIAL" : "COMPLETED");
      scan.setMessage(
          rootFailed
              ? "Không truy cập được trang bắt đầu. Xem chi tiết lỗi."
              : limited
                  ? "Đã dừng theo giới hạn trang/liên kết/độ sâu 2 hoặc 90 giây. Đây là kết quả của"
                      + " phần đã quét."
                  : "Đã kiểm tra tất cả liên kết tìm thấy trong phạm vi quét.");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      scan.setStatus("INTERRUPTED");
      scan.setMessage("Lần quét bị gián đoạn. Có thể chạy lại.");
    } catch (Exception ex) {
      scan.setStatus("FAILED");
      scan.setMessage(abbreviate(ex.getMessage(), 1000));
    } finally {
      scan.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
      try {
        progress(scan, results);
      } finally {
        running.remove(scan.getWebsiteId());
      }
    }
  }

  private void progress(LinkScan scan, List<LinkResult> results) {
    scan.setChecked(results.size());
    scan.setBroken((int) results.stream().filter(r -> r.status().equals("BROKEN")).count());
    scan.setUncertain(
        (int) results.stream().filter(r -> Set.of("ERROR", "REVIEW").contains(r.status())).count());
    try {
      scan.setResultsJson(json.writeValueAsString(results));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    scans.save(scan);
  }

  private static String abbreviate(String value, int max) {
    return value == null
        ? "Lỗi không xác định."
        : value.substring(0, Math.min(value.length(), max));
  }

  private static String origin(URI uri) {
    return uri.getScheme()
        + "://"
        + uri.getHost().toLowerCase(Locale.ROOT)
        + ":"
        + (uri.getPort() >= 0 ? uri.getPort() : uri.getScheme().equals("https") ? 443 : 80);
  }

  private static URI normalize(URI uri) {
    if (!uri.isAbsolute()
        || uri.getHost() == null
        || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
        || uri.getUserInfo() != null
        || uri.toString().length() > 2000) return null;
    String raw = uri.toString().split("#", 2)[0];
    URI clean = URI.create(raw).normalize();
    return URI.create(
        clean.getScheme().toLowerCase(Locale.ROOT)
            + "://"
            + clean.getRawAuthority().toLowerCase(Locale.ROOT)
            + (clean.getRawPath().isEmpty() ? "/" : clean.getRawPath())
            + (clean.getRawQuery() == null ? "" : "?" + clean.getRawQuery()));
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
