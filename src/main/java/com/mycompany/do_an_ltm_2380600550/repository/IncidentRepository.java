package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
  java.util.Optional<Incident> findFirstByWebsiteIdAndStatusOrderByIdDesc(
      Long websiteId, String status);

  boolean existsByOpeningCheckLogId(Long id);

  org.springframework.data.domain.Page<Incident> findByWebsiteIdOrderByIdDesc(
      Long id, org.springframework.data.domain.Pageable page);

  java.util.List<Incident> findByWebsiteIdOrderByIdDesc(Long id);
}
