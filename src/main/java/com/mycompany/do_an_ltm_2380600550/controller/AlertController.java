package com.mycompany.do_an_ltm_2380600550.controller;

import com.mycompany.do_an_ltm_2380600550.dto.AlertPageResponse;
import com.mycompany.do_an_ltm_2380600550.entity.AlertEventType;
import com.mycompany.do_an_ltm_2380600550.service.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
public class AlertController {
    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public AlertPageResponse getAlerts(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "8") int size,
                                        @RequestParam(required = false) Long websiteId,
                                        @RequestParam(required = false) AlertEventType eventType) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page phải từ 0 trở lên; size phải từ 1 đến 100.");
        }
        return AlertPageResponse.from(alertService.getAlerts(page, size, websiteId, eventType));
    }
}
