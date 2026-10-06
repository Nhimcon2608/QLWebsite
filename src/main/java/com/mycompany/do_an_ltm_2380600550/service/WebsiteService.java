package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.entity.UptimeCheckLog;
import com.mycompany.do_an_ltm_2380600550.entity.Website;
import com.mycompany.do_an_ltm_2380600550.repository.UptimeCheckLogRepository;
import com.mycompany.do_an_ltm_2380600550.repository.WebsiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class WebsiteService {

    private final WebsiteRepository websiteRepository;
    private final UptimeCheckLogRepository logRepository;
    private final AlertService alertService;

    public WebsiteService(WebsiteRepository websiteRepository, UptimeCheckLogRepository logRepository,
                          AlertService alertService) {
        this.websiteRepository = websiteRepository;
        this.logRepository = logRepository;
        this.alertService = alertService;
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Lấy danh sách tất cả website
    public List<Website> getAllWebsites() {
        return websiteRepository.findAll();
    }

    // Lấy thông tin 1 website theo ID
    public Optional<Website> getWebsiteById(Long id) {
        return websiteRepository.findById(id);
    }

    // Thêm mới hoặc cập nhật website
    public Website saveWebsite(Website website) {
        return websiteRepository.save(website);
    }

    // Xóa website
    public void deleteWebsite(Long id) {
        websiteRepository.deleteById(id);
    }

    // Thực hiện kiểm tra trạng thái 1 website (Ping HTTP Request)
    @Transactional
    public UptimeCheckLog pingWebsite(Website website) {
        website = websiteRepository.findByIdForUpdate(website.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website không tồn tại."));
        if (!Boolean.TRUE.equals(website.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bật website trước khi kiểm tra.");
        }
        UptimeCheckLog previous = logRepository.findFirstByWebsiteIdOrderByIdDesc(website.getId()).orElse(null);
        long startTime = System.nanoTime();
        Integer statusCode = null;
        boolean isUp = false;
        String errorMessage = null;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(website.getUrl()))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            statusCode = response.statusCode();

            // HTTP status 2xx và 3xx được tính là website đang hoạt động
            if (statusCode >= 200 && statusCode < 400) {
                isUp = true;
            } else {
                errorMessage = "HTTP Error Status: " + statusCode;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = "Kiểm tra bị gián đoạn.";
        } catch (Exception e) {
            errorMessage = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage() : e.getClass().getSimpleName() + ": Không thể kết nối đến website.";
        }

        long responseTimeMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
        if (errorMessage != null && errorMessage.length() > 1000) errorMessage = errorMessage.substring(0, 1000);

        UptimeCheckLog log = new UptimeCheckLog(website, statusCode, responseTimeMs, isUp, errorMessage);
        log = logRepository.save(log);
        alertService.recordTransition(previous, log);
        return log;
    }

    // Lấy lịch sử kiểm tra của 1 website
    public List<UptimeCheckLog> getLogsByWebsiteId(Long websiteId) {
        return logRepository.findTop20ByWebsiteIdOrderByIdDesc(websiteId);
    }
}
