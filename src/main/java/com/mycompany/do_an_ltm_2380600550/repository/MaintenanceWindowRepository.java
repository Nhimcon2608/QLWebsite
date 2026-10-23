package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.MaintenanceWindow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, Long> {
  boolean existsByWebsiteIdAndStartsAtLessThanEqualAndEndsAtGreaterThan(
      Long id, java.time.LocalDateTime start, java.time.LocalDateTime end);

  java.util.List<MaintenanceWindow> findByWebsiteIdOrderByStartsAtDesc(Long id);
}
