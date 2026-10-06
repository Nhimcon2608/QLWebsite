package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.AlertEventType;
import com.mycompany.do_an_ltm_2380600550.entity.WebsiteAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebsiteAlertRepository extends JpaRepository<WebsiteAlert, Long> {
    boolean existsByCheckLogId(Long checkLogId);

    @Query("select a from WebsiteAlert a where (:websiteId is null or a.websiteId = :websiteId) "
            + "and (:eventType is null or a.eventType = :eventType)")
    Page<WebsiteAlert> search(@Param("websiteId") Long websiteId,
                             @Param("eventType") AlertEventType eventType,
                             Pageable pageable);
}
