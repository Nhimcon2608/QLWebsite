package com.mycompany.do_an_ltm_2380600550.controller;

import com.mycompany.do_an_ltm_2380600550.entity.LinkScan;
import com.mycompany.do_an_ltm_2380600550.service.LinkScanService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/websites/{id}/link-scans")
public class LinkScanController {
  private final LinkScanService scans;

  public LinkScanController(LinkScanService scans) {
    this.scans = scans;
  }

  public record Input(Integer maxPages, Integer maxLinks, Boolean external) {}

  @GetMapping
  public List<LinkScan> list(@PathVariable Long id) {
    return scans.list(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public LinkScan start(@PathVariable Long id, @RequestBody Input input) {
    return scans.start(
        id,
        input.maxPages() == null ? 5 : input.maxPages(),
        input.maxLinks() == null ? 100 : input.maxLinks(),
        Boolean.TRUE.equals(input.external()));
  }

  @GetMapping("/{scanId}")
  public Map<String, Object> report(@PathVariable Long id, @PathVariable Long scanId) {
    return scans.report(id, scanId);
  }
}
