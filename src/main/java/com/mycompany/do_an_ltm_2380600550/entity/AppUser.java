package com.mycompany.do_an_ltm_2380600550.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 80)
  private String username;

  @JsonIgnore
  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false, length = 20)
  private String role = "VIEWER";

  private Boolean enabled = true;

  private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String value) {
    this.username = value;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String value) {
    this.passwordHash = value;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String value) {
    this.role = value;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean value) {
    this.enabled = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    this.createdAt = value;
  }
}
