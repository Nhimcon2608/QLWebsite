package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String channel;

  @Column(length = 300)
  private String subject;

  @Column(length = 4000)
  private String message;

  private String deliveryStatus = "PENDING";

  private Integer attempts = 0;

  @Column(length = 500)
  private String lastError;

  private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

  private LocalDateTime nextAttemptAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

  private LocalDateTime sentAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String value) {
    this.channel = value;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String value) {
    this.subject = value;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String value) {
    this.message = value;
  }

  public String getDeliveryStatus() {
    return deliveryStatus;
  }

  public void setDeliveryStatus(String value) {
    this.deliveryStatus = value;
  }

  public Integer getAttempts() {
    return attempts;
  }

  public void setAttempts(Integer value) {
    this.attempts = value;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String value) {
    this.lastError = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    this.createdAt = value;
  }

  public LocalDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(LocalDateTime value) {
    this.nextAttemptAt = value;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(LocalDateTime value) {
    this.sentAt = value;
  }
}
