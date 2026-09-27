package vn.qlwebsite.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    private final boolean demo;

    public PageController(@Value("${app.ui.demo:true}") boolean demo) {
        this.demo = demo;
    }

    @GetMapping({"/", "/websites", "/websites/{id}", "/alerts", "/login"})
    public String application(Model model) {
        model.addAttribute("demo", demo);
        return "app";
    }
}
