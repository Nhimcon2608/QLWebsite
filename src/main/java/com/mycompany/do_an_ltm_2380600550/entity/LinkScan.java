package com.mycompany.do_an_ltm_2380600550.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "link_scans",
    indexes = @Index(name = "ix_link_scan_website", columnList = "websiteId,id"))
public class LinkScan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long websiteId;
  private String status = "QUEUED";
  private LocalDateTime startedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  private LocalDateTime completedAt;
  private Integer checked = 0;
  private Integer broken = 0;
  private Integer uncertain = 0;
  private Integer pages = 0;
  private Integer maxPages;
  private Integer maxLinks;
  private Boolean external;

  @Column(length = 500)
  private String targetUrl;

  @Column(length = 1000)
  private String message;

  @JsonIgnore
  @Column(length = 2000000)
  private String resultsJson = "[]";

  public Long getId() {
    return id;
  }

  public Long getWebsiteId() {
    return websiteId;
  }

  public void setWebsiteId(Long value) {
    websiteId = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime value) {
    completedAt = value;
  }

  public Integer getChecked() {
    return checked;
  }

  public void setChecked(Integer value) {
    checked = value;
  }

  public Integer getBroken() {
    return broken;
  }

  public void setBroken(Integer value) {
    broken = value;
  }

  public Integer getUncertain() {
    return uncertain;
  }

  public void setUncertain(Integer value) {
    uncertain = value;
  }

  public Integer getPages() {
    return pages;
  }

  public void setPages(Integer value) {
    pages = value;
  }

  public Integer getMaxPages() {
    return maxPages;
  }

  public void setMaxPages(Integer value) {
    maxPages = value;
  }

  public Integer getMaxLinks() {
    return maxLinks;
  }

  public void setMaxLinks(Integer value) {
    maxLinks = value;
  }

  public Boolean getExternal() {
    return external;
  }

  public void setExternal(Boolean value) {
    external = value;
  }

  public String getTargetUrl() {
    return targetUrl;
  }

  public void setTargetUrl(String value) {
    targetUrl = value;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String value) {
    message = value;
  }

  public String getResultsJson() {
    return resultsJson;
  }

  public void setResultsJson(String value) {
    resultsJson = value;
  }
}
