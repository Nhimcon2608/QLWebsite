package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.Website;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebsiteRepository extends JpaRepository<Website, Long> {

    List<Website> findByIsActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Website w where w.id = :id")
    Optional<Website> findByIdForUpdate(@Param("id") Long id);
}
