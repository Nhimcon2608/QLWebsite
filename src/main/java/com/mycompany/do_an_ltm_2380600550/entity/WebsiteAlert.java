package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "website_alerts", indexes = {
        @Index(name = "idx_website_alerts_created", columnList = "created_at,id"),
        @Index(name = "idx_website_alerts_website", columnList = "website_id")
})
public class WebsiteAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_log_id", nullable = false, unique = true)
    private UptimeCheckLog checkLog;

    @Column(nullable = false)
    private Long websiteId;

    @Column(nullable = false)
    private String websiteName;

    @Column(nullable = false, length = 500)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertEventType eventType;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WebsiteAlert() {}

    public WebsiteAlert(UptimeCheckLog checkLog, AlertEventType eventType, String message) {
        this.checkLog = checkLog;
        this.websiteId = checkLog.getWebsite().getId();
        this.websiteName = checkLog.getWebsite().getName();
        this.websiteUrl = checkLog.getWebsite().getUrl();
        this.eventType = eventType;
        this.message = message.length() > 2000 ? message.substring(0, 2000) : message;
        this.createdAt = checkLog.getCheckedAt();
    }

    public Long getId() { return id; }
    public UptimeCheckLog getCheckLog() { return checkLog; }
    public Long getWebsiteId() { return websiteId; }
    public String getWebsiteName() { return websiteName; }
    public String getWebsiteUrl() { return websiteUrl; }
    public AlertEventType getEventType() { return eventType; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
