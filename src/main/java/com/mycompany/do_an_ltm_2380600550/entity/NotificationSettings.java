package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_settings")
public class NotificationSettings {
  @Id private Long id = 1L;

  private Boolean emailEnabled = false;

  @Column(length = 1000)
  private String emailTo = "";

  private Boolean telegramEnabled = false;

  private String telegramChatId = "";

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Boolean getEmailEnabled() {
    return emailEnabled;
  }

  public void setEmailEnabled(Boolean value) {
    this.emailEnabled = value;
  }

  public String getEmailTo() {
    return emailTo;
  }

  public void setEmailTo(String value) {
    this.emailTo = value;
  }

  public Boolean getTelegramEnabled() {
    return telegramEnabled;
  }

  public void setTelegramEnabled(Boolean value) {
    this.telegramEnabled = value;
  }

  public String getTelegramChatId() {
    return telegramChatId;
  }

  public void setTelegramChatId(String value) {
    this.telegramChatId = value;
  }
}
