package vn.qlwebsite.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Same-origin bridge to the separately running backend branch. */
@RestController
@ConditionalOnProperty(name = "app.ui.backend", havingValue = "true")
public class BackendProxyController {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final String backendUrl;

    public BackendProxyController(@Value("${app.backend.url:http://127.0.0.1:8082}") String backendUrl) {
        this.backendUrl = backendUrl;
    }

    @RequestMapping("/backend-api/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request,
                                          @RequestBody(required = false) byte[] body) {
        String path = request.getRequestURI().substring("/backend-api".length());
        if (!path.matches("/(?:alerts|websites(?:/[0-9]+(?:/(?:check|logs))?)?)")) {
            return ResponseEntity.notFound().build();
        }
        if (!java.util.Set.of("GET", "POST", "PUT", "DELETE").contains(request.getMethod())) {
            return ResponseEntity.status(405).build();
        }
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        HttpRequest outgoing = HttpRequest.newBuilder(URI.create(backendUrl + "/api" + path + query))
                .timeout(Duration.ofSeconds(18))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(request.getMethod(), body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body)).build();
        try {
            var response = client.send(outgoing, HttpResponse.BodyHandlers.ofByteArray());
            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON).body(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return unavailable();
        } catch (IOException error) {
            return unavailable();
        }
    }

    private ResponseEntity<byte[]> unavailable() {
        return ResponseEntity.status(502).contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Không kết nối được backend. Hãy khởi động backend rồi thử lại.\"}"
                        .getBytes(StandardCharsets.UTF_8));
    }
}
