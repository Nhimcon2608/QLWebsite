package com.mycompany.do_an_ltm_2380600550.config;

import com.mycompany.do_an_ltm_2380600550.repository.WebsiteRepository;
import com.mycompany.do_an_ltm_2380600550.service.*;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringJobs {
  private final WebsiteRepository websites;
  private final WebsiteService checks;
  private final Set<Long> running = ConcurrentHashMap.newKeySet();
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(
          4,
          4,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(100),
          new ThreadPoolExecutor.AbortPolicy());
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(MonitoringJobs.class);

  public MonitoringJobs(WebsiteRepository websites, WebsiteService checks) {
    this.websites = websites;
    this.checks = checks;
  }

  @Scheduled(
      fixedDelayString = "${app.monitor.scan-ms:1000}",
      initialDelayString = "${app.monitor.initial-delay-ms:5000}")
  public void scan() {
    var now = LocalDateTime.now(java.time.ZoneOffset.UTC);
    for (var w : websites.findByIsActiveTrue()) {
      if (w.getArchivedAt() != null
          || (w.getNextCheckAt() != null && now.isBefore(w.getNextCheckAt()))
          || !running.add(w.getId())) continue;
      try {
        workers.execute(
            () -> {
              try {
                checks.check(w.getId(), "SCHEDULED");
              } catch (Exception ex) {
                log.warn(
                    "Kiểm tra website {} thất bại: {}", w.getId(), ex.getClass().getSimpleName());
              } finally {
                running.remove(w.getId());
              }
            });
      } catch (RejectedExecutionException ex) {
        running.remove(w.getId());
      }
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
