package com.mycompany.do_an_ltm_2380600550.repository;

import com.mycompany.do_an_ltm_2380600550.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  java.util.Optional<AppUser> findByUsername(String username);

  boolean existsByUsername(String username);

  long countByRoleAndEnabledTrue(String role);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select u from AppUser u where u.role='ADMIN' and u.enabled=true order by u.id")
  java.util.List<AppUser> lockActiveAdmins();
}
