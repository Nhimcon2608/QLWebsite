package com.mycompany.do_an_ltm_2380600550.config;

import com.mycompany.do_an_ltm_2380600550.entity.AppUser;
import com.mycompany.do_an_ltm_2380600550.repository.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class DefaultAdminInitializer implements ApplicationRunner {
  private final AppUserRepository users;
  private final PasswordEncoder passwords;

  public DefaultAdminInitializer(AppUserRepository users, PasswordEncoder passwords) {
    this.users = users;
    this.passwords = passwords;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    // Seed once. Restarting must preserve existing accounts and changed passwords.
    if (users.count() != 0) return;
    var admin = new AppUser();
    admin.setUsername("admin");
    admin.setPasswordHash(passwords.encode("admin123"));
    admin.setRole("ADMIN");
    admin.setEnabled(true);
    users.save(admin);
  }
}
