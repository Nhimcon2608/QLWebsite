package com.mycompany.do_an_ltm_2380600550.dto;

import com.mycompany.do_an_ltm_2380600550.entity.AlertEventType;
import com.mycompany.do_an_ltm_2380600550.entity.WebsiteAlert;
import java.time.LocalDateTime;

public record AlertResponse(Long id, Long websiteId, String websiteName, String websiteUrl,
                            AlertEventType eventType, String message, LocalDateTime createdAt,
                            Long checkLogId) {
    public static AlertResponse from(WebsiteAlert alert) {
        return new AlertResponse(alert.getId(), alert.getWebsiteId(), alert.getWebsiteName(),
                alert.getWebsiteUrl(), alert.getEventType(), alert.getMessage(), alert.getCreatedAt(),
                alert.getCheckLog().getId());
    }
}
