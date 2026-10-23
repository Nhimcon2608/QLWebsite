package com.mycompany.do_an_ltm_2380600550.config;

import com.mycompany.do_an_ltm_2380600550.repository.WebsiteRepository;
import com.mycompany.do_an_ltm_2380600550.service.AlertService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AlertHistoryInitializer implements ApplicationRunner {
    private final WebsiteRepository websiteRepository;
    private final AlertService alertService;

    public AlertHistoryInitializer(WebsiteRepository websiteRepository, AlertService alertService) {
        this.websiteRepository = websiteRepository;
        this.alertService = alertService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        for (var website : websiteRepository.findAll()) {
            alertService.synchronizeWebsiteHistory(website.getId());
        }
    }
}
