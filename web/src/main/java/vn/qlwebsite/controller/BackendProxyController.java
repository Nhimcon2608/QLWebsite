package vn.qlwebsite.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Same-origin bridge to the separately running backend branch. */
@RestController
@ConditionalOnProperty(name = "app.ui.backend", havingValue = "true")
public class BackendProxyController {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final String backendUrl;

  public BackendProxyController(
      @Value("${app.backend.url:http://127.0.0.1:8082}") String backendUrl) {
    this.backendUrl = backendUrl;
  }

  @RequestMapping("/backend-api/**")
  public ResponseEntity<byte[]> forward(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    String path = request.getRequestURI().substring("/backend-api".length());
    if (!path.matches(
        "/(?:csrf|session|dashboard/summary|alerts|reports/export|users(?:/[0-9]+)?|incidents/[0-9]+|websites(?:/import(?:/preview)?|/[0-9]+(?:/(?:check|logs|history|metrics|incidents|link-scans(?:/[0-9]+)?|maintenance(?:/[0-9]+)?))?)?)")) {
      return ResponseEntity.notFound().build();
    }
    if (!java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())) {
      return ResponseEntity.status(405).build();
    }
    String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
    if (body != null && body.length > 6_100_000) return ResponseEntity.status(413).build();
    var builder =
        HttpRequest.newBuilder(URI.create(backendUrl + "/api" + path + query))
            .timeout(Duration.ofSeconds(70))
            .header("Accept", "application/json, text/csv")
            .header("Content-Type", "application/json")
            .method(
                request.getMethod(),
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body));
    if (request.getCookies() != null) {
      for (var cookie : request.getCookies()) {
        if (cookie.getName().equals("QLSESSION") && cookie.getValue().matches("[A-Za-z0-9.\\-]+"))
          builder.header("Cookie", "QLSESSION=" + cookie.getValue());
      }
    }
    String csrf = request.getHeader("X-CSRF-TOKEN");
    if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
    var outgoing = builder.build();
    try {
      var response = client.send(outgoing, HttpResponse.BodyHandlers.ofByteArray());
      var headers = new org.springframework.http.HttpHeaders();
      headers.set(
          "Content-Type", response.headers().firstValue("Content-Type").orElse("application/json"));
      headers.set("Cache-Control", "no-store");
      response.headers().allValues("Set-Cookie").forEach(v -> headers.add("Set-Cookie", v));
      response
          .headers()
          .firstValue("Content-Disposition")
          .ifPresent(v -> headers.set("Content-Disposition", v));
      return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return unavailable();
    } catch (IOException error) {
      return unavailable();
    }
  }

  private ResponseEntity<byte[]> unavailable() {
    return ResponseEntity.status(502)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            "{\"message\":\"Không kết nối được backend. Hãy khởi động backend rồi thử lại.\"}"
                .getBytes(StandardCharsets.UTF_8));
  }
}
