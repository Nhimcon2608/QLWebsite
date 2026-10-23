package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.entity.*;
import com.mycompany.do_an_ltm_2380600550.repository.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService implements ApplicationRunner {
  private final NotificationSettingsRepository settings;
  private final NotificationDeliveryRepository deliveries;
  private final ObjectProvider<JavaMailSender> mail;
  private final String token, from, host;
  private final boolean externalEnabled;
  private final com.fasterxml.jackson.databind.ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public NotificationService(
      NotificationSettingsRepository settings,
      NotificationDeliveryRepository deliveries,
      ObjectProvider<JavaMailSender> mail,
      @Value("${app.telegram.bot-token:}") String token,
      @Value("${app.mail.from:qlwebsite@localhost}") String from,
      @Value("${spring.mail.host:}") String host,
      @Value("${app.notifications.external-enabled:false}") boolean externalEnabled,
      com.fasterxml.jackson.databind.ObjectMapper json) {
    this.settings = settings;
    this.deliveries = deliveries;
    this.mail = mail;
    this.token = token;
    this.from = from;
    this.host = host;
    this.externalEnabled = externalEnabled;
    this.json = json;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!externalEnabled) deliveries.cancelPending();
  }

  public NotificationSettings settings() {
    return settings.findById(1L).orElseGet(NotificationSettings::new);
  }

  public boolean smtpConfigured() {
    return externalEnabled && !host.isBlank() && mail.getIfAvailable() != null;
  }

  public boolean telegramConfigured() {
    return externalEnabled && !token.isBlank();
  }

  public void requireExternalEnabled() {
    if (!externalEnabled)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.GONE,
          "Gửi Email/Telegram đã được tắt. Xem cảnh báo ngay trong ứng dụng.");
  }

  @Transactional
  public NotificationSettings save(NotificationSettings s) {
    requireExternalEnabled();
    if (Boolean.TRUE.equals(s.getEmailEnabled())) {
      if (!smtpConfigured()
          || s.getEmailTo() == null
          || !s.getEmailTo().matches("[^\\s,@]+@[^\\s,@]+(?:\\s*,\\s*[^\\s,@]+@[^\\s,@]+)*"))
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "Cấu hình SMTP và nhập địa chỉ email hợp lệ, phân cách bằng dấu phẩy.");
    }
    if (Boolean.TRUE.equals(s.getTelegramEnabled())
        && (!telegramConfigured()
            || s.getTelegramChatId() == null
            || !s.getTelegramChatId().matches("(?:-?\\d+|@[A-Za-z0-9_]+)")))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Cấu hình TELEGRAM_BOT_TOKEN và Chat ID hợp lệ.");
    s.setId(1L);
    return settings.save(s);
  }

  @Transactional
  public void enqueue(String subject, String message) {
    if (!externalEnabled) return;
    var s = settings();
    if (Boolean.TRUE.equals(s.getEmailEnabled())) add("EMAIL", subject, message);
    if (Boolean.TRUE.equals(s.getTelegramEnabled())) add("TELEGRAM", subject, message);
  }

  private void add(String channel, String subject, String message) {
    var d = new NotificationDelivery();
    d.setChannel(channel);
    d.setSubject(subject);
    d.setMessage(message.length() > 3900 ? message.substring(0, 3900) : message);
    deliveries.save(d);
  }

  public List<NotificationDelivery> recent() {
    return deliveries.findTop30ByOrderByIdDesc();
  }

  // Outbox rows commit with the check. Delivery failures never roll back monitoring data.
  @Transactional
  public synchronized void deliverPending() {
    if (!externalEnabled) {
      deliveries.cancelPending();
      return;
    }
    for (var d :
        deliveries.findTop20ByDeliveryStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            "PENDING", LocalDateTime.now(java.time.ZoneOffset.UTC))) {
      try {
        var s = settings();
        if (d.getChannel().equals("EMAIL")) {
          if (!Boolean.TRUE.equals(s.getEmailEnabled())) {
            d.setDeliveryStatus("CANCELLED");
            continue;
          }
          var sender = mail.getIfAvailable();
          if (sender == null) throw new IllegalStateException("SMTP chưa được cấu hình.");
          var msg = new SimpleMailMessage();
          msg.setFrom(from);
          msg.setTo(s.getEmailTo().split("\\s*,\\s*"));
          msg.setSubject(d.getSubject());
          msg.setText(d.getMessage());
          sender.send(msg);
        } else {
          if (!Boolean.TRUE.equals(s.getTelegramEnabled())) {
            d.setDeliveryStatus("CANCELLED");
            continue;
          }
          if (token.isBlank()) throw new IllegalStateException("Telegram chưa được cấu hình.");
          var request =
              HttpRequest.newBuilder(
                      URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                  .timeout(Duration.ofSeconds(10))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          json.writeValueAsString(
                              Map.of(
                                  "chat_id",
                                  s.getTelegramChatId(),
                                  "text",
                                  d.getSubject() + "\n" + d.getMessage()))))
                  .build();
          var response = http.send(request, HttpResponse.BodyHandlers.ofString());
          if (response.statusCode() != 200)
            throw new IllegalStateException("Telegram trả về HTTP " + response.statusCode());
        }
        d.setDeliveryStatus("SENT");
        d.setSentAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        d.setLastError(null);
      } catch (Exception ex) {
        if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        // Never persist provider exception messages: they can contain credentials or the bot token.
        d.setLastError(
            "Không gửi được thông báo qua " + d.getChannel() + ". Kiểm tra cấu hình và kết nối.");
        d.setNextAttemptAt(
            LocalDateTime.now(java.time.ZoneOffset.UTC)
                .plusSeconds((long) Math.pow(2, d.getAttempts()) * 30));
        if (d.getAttempts() >= 4) d.setDeliveryStatus("FAILED");
      }
      d.setAttempts(d.getAttempts() + 1);
    }
  }
}
