package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.Website;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebsiteRepository extends JpaRepository<Website, Long> {

    List<Website> findByIsActiveTrue();
}
