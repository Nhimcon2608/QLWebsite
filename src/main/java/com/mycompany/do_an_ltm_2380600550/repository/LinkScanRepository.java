package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.LinkScan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkScanRepository extends JpaRepository<LinkScan, Long> {
  List<LinkScan> findTop10ByWebsiteIdOrderByIdDesc(Long websiteId);

  List<LinkScan> findByStatusIn(List<String> statuses);
}
