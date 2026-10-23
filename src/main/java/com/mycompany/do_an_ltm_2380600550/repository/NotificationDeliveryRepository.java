package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
  @Modifying(clearAutomatically = true)
  @Query(
      "update NotificationDelivery d set d.deliveryStatus = 'CANCELLED',"
          + " d.lastError = 'Gửi Email/Telegram đã được tắt.' where d.deliveryStatus = 'PENDING'")
  int cancelPending();

  java.util.List<NotificationDelivery>
      findTop20ByDeliveryStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
          String status, java.time.LocalDateTime now);

  java.util.List<NotificationDelivery> findTop30ByOrderByIdDesc();
}
