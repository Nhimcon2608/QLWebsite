package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.dto.AlertResponse;
import com.mycompany.do_an_ltm_2380600550.entity.AlertEventType;
import com.mycompany.do_an_ltm_2380600550.entity.UptimeCheckLog;
import com.mycompany.do_an_ltm_2380600550.entity.WebsiteAlert;
import com.mycompany.do_an_ltm_2380600550.repository.UptimeCheckLogRepository;
import com.mycompany.do_an_ltm_2380600550.repository.WebsiteAlertRepository;
import com.mycompany.do_an_ltm_2380600550.repository.WebsiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {
    private final WebsiteAlertRepository alertRepository;
    private final UptimeCheckLogRepository logRepository;
    private final WebsiteRepository websiteRepository;

    public AlertService(WebsiteAlertRepository alertRepository, UptimeCheckLogRepository logRepository,
                        WebsiteRepository websiteRepository) {
        this.alertRepository = alertRepository;
        this.logRepository = logRepository;
        this.websiteRepository = websiteRepository;
    }

    // Called while holding the website lock, in the same transaction as the HTTP result.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransition(UptimeCheckLog previous, UptimeCheckLog current) {
        boolean isUp = Boolean.TRUE.equals(current.getIsUp());
        boolean wasUp = previous != null && Boolean.TRUE.equals(previous.getIsUp());
        AlertEventType type;
        if (!isUp && (previous == null || wasUp)) {
            type = AlertEventType.DOWN;
        } else if (isUp && previous != null && !wasUp) {
            type = AlertEventType.RECOVERY;
        } else {
            return;
        }
        if (alertRepository.existsByCheckLogId(current.getId())) return;

        String name = current.getWebsite().getName();
        String message;
        if (type == AlertEventType.RECOVERY) {
            message = name + " đã hoạt động trở lại. HTTP " + current.getStatusCode()
                    + " · Phản hồi " + current.getResponseTimeMs() + " ms.";
        } else {
            String reason = current.getStatusCode() != null ? "HTTP " + current.getStatusCode()
                    : current.getErrorMessage() != null && !current.getErrorMessage().isBlank()
                        ? current.getErrorMessage() : "Không thể kết nối đến website.";
            message = name + " đang ngừng hoạt động. " + reason;
        }
        alertRepository.save(new WebsiteAlert(current, type, message));
    }

    // Reconstruct actual historical transitions when upgrading an existing database.
    // The unique check_log_id and website lock also make repeated startup safe.
    @Transactional
    public void synchronizeWebsiteHistory(Long websiteId) {
        if (websiteRepository.findByIdForUpdate(websiteId).isEmpty()) return;
        UptimeCheckLog previous = null;
        for (UptimeCheckLog current : logRepository.findByWebsiteIdOrderByIdAsc(websiteId)) {
            recordTransition(previous, current);
            previous = current;
        }
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlerts(int page, int size, Long websiteId, AlertEventType eventType) {
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return alertRepository.search(websiteId, eventType, pageable).map(AlertResponse::from);
    }
}
