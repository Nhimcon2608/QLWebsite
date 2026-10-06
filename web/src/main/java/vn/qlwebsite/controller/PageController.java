package vn.qlwebsite.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    private final boolean demo;
    private final boolean backend;

    public PageController(@Value("${app.ui.demo:true}") boolean demo,
                          @Value("${app.ui.backend:false}") boolean backend) {
        this.demo = demo && !backend;
        this.backend = backend;
    }

    @GetMapping({"/", "/websites", "/websites/{id}", "/alerts", "/login"})
    public String application(Model model) {
        model.addAttribute("demo", demo);
        model.addAttribute("backend", backend);
        return "app";
    }
}
