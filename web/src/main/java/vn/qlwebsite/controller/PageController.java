package vn.qlwebsite.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.qlwebsite.config.AssetConfig;

@Controller
public class PageController {
  private final boolean demo;
  private final boolean backend;
  private final AssetConfig assets;

  public PageController(
      @Value("${app.ui.demo:true}") boolean demo,
      @Value("${app.ui.backend:false}") boolean backend,
      AssetConfig assets) {
    this.demo = demo && !backend;
    this.backend = backend;
    this.assets = assets;
  }

  @GetMapping({"/", "/websites", "/websites/{id}", "/alerts", "/login", "/users"})
  public String application(Model model, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    model.addAttribute("demo", demo);
    model.addAttribute("backend", backend);
    model.addAttribute("assetBase", assets.basePath());
    return "app";
  }

  @GetMapping("/status")
  public String removedStatusPage(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return "redirect:/websites";
  }

  @GetMapping("/settings")
  public String removedNotificationPage(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return "redirect:/alerts";
  }
}
