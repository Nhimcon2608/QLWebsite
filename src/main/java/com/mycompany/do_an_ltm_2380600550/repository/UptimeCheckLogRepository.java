package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.UptimeCheckLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UptimeCheckLogRepository extends JpaRepository<UptimeCheckLog, Long> {

    // Lấy 20 lịch sử kiểm tra gần nhất của 1 website cụ thể
    List<UptimeCheckLog> findTop20ByWebsiteIdOrderByCheckedAtDesc(Long websiteId);
}
