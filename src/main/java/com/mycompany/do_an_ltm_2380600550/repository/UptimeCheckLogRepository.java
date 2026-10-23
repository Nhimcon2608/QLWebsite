package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.UptimeCheckLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UptimeCheckLogRepository extends JpaRepository<UptimeCheckLog, Long> {

  @org.springframework.data.jpa.repository.Query(
      "select l from UptimeCheckLog l where l.website.id=:id and l.checkedAt>=:from and"
          + " l.checkedAt<:to and (:success is null or l.isUp=:success)")
  org.springframework.data.domain.Page<UptimeCheckLog> history(
      @org.springframework.data.repository.query.Param("id") Long id,
      @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
      @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
      @org.springframework.data.repository.query.Param("success") Boolean success,
      org.springframework.data.domain.Pageable page);

  List<UptimeCheckLog>
      findByWebsiteIdAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(
          Long id, java.time.LocalDateTime from, java.time.LocalDateTime to);

  // Lấy 20 lịch sử kiểm tra gần nhất của 1 website cụ thể
  List<UptimeCheckLog> findTop20ByWebsiteIdOrderByIdDesc(Long websiteId);

  Optional<UptimeCheckLog> findFirstByWebsiteIdOrderByIdDesc(Long websiteId);

  List<UptimeCheckLog> findByWebsiteIdOrderByIdAsc(Long websiteId);
}
