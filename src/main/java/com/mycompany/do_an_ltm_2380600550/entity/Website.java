package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "websites")
public class Website {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    // Tần suất kiểm tra (tính bằng giây), mặc định 60s
    private Integer checkIntervalSeconds = 60;

    // Trạng thái cho phép theo dõi hay không
    private Boolean isActive = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Website() {
    }

    public Website(String name, String url, Integer checkIntervalSeconds, Boolean isActive) {
        this.name = name;
        this.url = url;
        if (checkIntervalSeconds != null) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public void setCheckIntervalSeconds(Integer checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
