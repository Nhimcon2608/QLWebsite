package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "uptime_check_logs")
public class UptimeCheckLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "website_id", nullable = false)
    private Website website;

    private Integer statusCode; // HTTP Status Code (200, 404, 500,...)
    private Long responseTimeMs; // Thời gian phản hồi (mili giây)
    private Boolean isUp; // true = hoạt động bình thường, false = down
    
    @Column(length = 1000)
    private String errorMessage; // Lưu lỗi nếu không kết nối được

    private LocalDateTime checkedAt = LocalDateTime.now();

    public UptimeCheckLog() {
    }

    public UptimeCheckLog(Website website, Integer statusCode, Long responseTimeMs, Boolean isUp, String errorMessage) {
        this.website = website;
        this.statusCode = statusCode;
        this.responseTimeMs = responseTimeMs;
        this.isUp = isUp;
        this.errorMessage = errorMessage;
        this.checkedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Website getWebsite() { return website; }
    public void setWebsite(Website website) { this.website = website; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public Long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public Boolean getIsUp() { return isUp; }
    public void setIsUp(Boolean isUp) { this.isUp = isUp; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}