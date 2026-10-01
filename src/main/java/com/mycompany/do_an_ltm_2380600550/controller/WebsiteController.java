package com.mycompany.do_an_ltm_2380600550.controller;

import com.mycompany.do_an_ltm_2380600550.entity.UptimeCheckLog;
import com.mycompany.do_an_ltm_2380600550.entity.Website;
import com.mycompany.do_an_ltm_2380600550.service.WebsiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/websites")
@CrossOrigin(origins = "*") // Cho phép gọi API từ giao diện Web/Dashboard
public class WebsiteController {

    @Autowired
    private WebsiteService websiteService;

    // 1. Lấy danh sách website
    @GetMapping
    public List<Website> getAllWebsites() {
        return websiteService.getAllWebsites();
    }

    // 2. Lấy chi tiết 1 website
    @GetMapping("/{id}")
    public ResponseEntity<Website> getWebsiteById(@PathVariable Long id) {
        return websiteService.getWebsiteById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. Thêm mới website
    @PostMapping
    public Website createWebsite(@RequestBody Website website) {
        return websiteService.saveWebsite(website);
    }

    // 4. Cập nhật website
    @PutMapping("/{id}")
    public ResponseEntity<Website> updateWebsite(@PathVariable Long id, @RequestBody Website websiteDetails) {
        return websiteService.getWebsiteById(id).map(website -> {
            website.setName(websiteDetails.getName());
            website.setUrl(websiteDetails.getUrl());
            website.setCheckIntervalSeconds(websiteDetails.getCheckIntervalSeconds());
            website.setIsActive(websiteDetails.getIsActive());
            return ResponseEntity.ok(websiteService.saveWebsite(website));
        }).orElse(ResponseEntity.notFound().build());
    }

    // 5. Xóa website
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebsite(@PathVariable Long id) {
        if (websiteService.getWebsiteById(id).isPresent()) {
            websiteService.deleteWebsite(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // 6. Kích hoạt kiểm tra ngay lập tức (Manual Check)
    @PostMapping("/{id}/check")
    public ResponseEntity<UptimeCheckLog> checkWebsiteNow(@PathVariable Long id) {
        return websiteService.getWebsiteById(id)
                .map(website -> ResponseEntity.ok(websiteService.pingWebsite(website)))
                .orElse(ResponseEntity.notFound().build());
    }

    // 7. Xem lịch sử kiểm tra của 1 website
    @GetMapping("/{id}/logs")
    public List<UptimeCheckLog> getWebsiteLogs(@PathVariable Long id) {
        return websiteService.getLogsByWebsiteId(id);
    }
}
