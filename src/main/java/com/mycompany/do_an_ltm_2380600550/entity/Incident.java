package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "monitor_incidents")
public class Incident {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long websiteId;

  @Column(unique = true)
  private Long openingCheckLogId;

  private LocalDateTime firstFailureAt;

  private LocalDateTime detectedAt;

  private LocalDateTime resolvedAt;

  @Column(length = 1000)
  private String reason;

  private String status = "OPEN";

  @Column(length = 4000)
  private String notes = "";

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getWebsiteId() {
    return websiteId;
  }

  public void setWebsiteId(Long value) {
    this.websiteId = value;
  }

  public Long getOpeningCheckLogId() {
    return openingCheckLogId;
  }

  public void setOpeningCheckLogId(Long value) {
    this.openingCheckLogId = value;
  }

  public LocalDateTime getFirstFailureAt() {
    return firstFailureAt;
  }

  public void setFirstFailureAt(LocalDateTime value) {
    this.firstFailureAt = value;
  }

  public LocalDateTime getDetectedAt() {
    return detectedAt;
  }

  public void setDetectedAt(LocalDateTime value) {
    this.detectedAt = value;
  }

  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(LocalDateTime value) {
    this.resolvedAt = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String value) {
    this.notes = value;
  }
}
