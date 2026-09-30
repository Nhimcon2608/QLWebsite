package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.entity.UptimeCheckLog;
import com.mycompany.do_an_ltm_2380600550.entity.Website;
import com.mycompany.do_an_ltm_2380600550.repository.UptimeCheckLogRepository;
import com.mycompany.do_an_ltm_2380600550.repository.WebsiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class WebsiteService {

    @Autowired
    private WebsiteRepository websiteRepository;

    @Autowired
    private UptimeCheckLogRepository logRepository;

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
    public UptimeCheckLog pingWebsite(Website website) {
        long startTime = System.currentTimeMillis();
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
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        long responseTimeMs = System.currentTimeMillis() - startTime;

        UptimeCheckLog log = new UptimeCheckLog(website, statusCode, responseTimeMs, isUp, errorMessage);
        return logRepository.save(log);
    }

    // Lấy lịch sử kiểm tra của 1 website
    public List<UptimeCheckLog> getLogsByWebsiteId(Long websiteId) {
        return logRepository.findTop20ByWebsiteIdOrderByCheckedAtDesc(websiteId);
    }
}
