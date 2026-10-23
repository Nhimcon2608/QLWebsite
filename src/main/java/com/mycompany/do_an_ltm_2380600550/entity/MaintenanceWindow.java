package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_windows")
public class MaintenanceWindow {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long websiteId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false)
  private LocalDateTime startsAt;

  @Column(nullable = false)
  private LocalDateTime endsAt;

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

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  public LocalDateTime getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(LocalDateTime value) {
    this.startsAt = value;
  }

  public LocalDateTime getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(LocalDateTime value) {
    this.endsAt = value;
  }
}
