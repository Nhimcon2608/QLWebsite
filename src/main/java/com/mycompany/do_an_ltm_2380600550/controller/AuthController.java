package com.mycompany.do_an_ltm_2380600550.controller;

import com.mycompany.do_an_ltm_2380600550.entity.AppUser;
import com.mycompany.do_an_ltm_2380600550.repository.AppUserRepository;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authentication;

  public AuthController(
      AppUserRepository users, PasswordEncoder encoder, AuthenticationManager authentication) {
    this.users = users;
    this.encoder = encoder;
    this.authentication = authentication;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/session")
  public Map<String, String> login(
      @RequestBody Credentials input, HttpServletRequest req, HttpServletResponse res) {
    try {
      var auth =
          authentication.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  input.username(), input.password()));
      if (req.getSession(false) != null) req.changeSessionId();
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      new HttpSessionSecurityContextRepository().saveContext(context, req, res);
      return identity(auth);
    } catch (org.springframework.security.core.AuthenticationException ex) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng.");
    }
  }

  @GetMapping("/session")
  public Map<String, String> session(Authentication auth) {
    return identity(auth);
  }

  @DeleteMapping("/session")
  public void logout(HttpServletRequest req) {
    if (req.getSession(false) != null) req.getSession(false).invalidate();
    SecurityContextHolder.clearContext();
  }

  @GetMapping("/users")
  public List<AppUser> users() {
    return users.findAll();
  }

  @PostMapping("/users")
  @Transactional
  public AppUser create(@RequestBody UserInput input) {
    validateCredentials(input.username(), input.password());
    validateRole(input.role());
    if (users.existsByUsername(input.username()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại.");
    var u = new AppUser();
    u.setUsername(input.username());
    u.setPasswordHash(encoder.encode(input.password()));
    u.setRole(input.role());
    return users.save(u);
  }

  @PutMapping("/users/{id}")
  @Transactional
  public synchronized AppUser update(@PathVariable Long id, @RequestBody UserInput input) {
    var activeAdmins = users.lockActiveAdmins();
    var u = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validateRole(input.role());
    if (u.getRole().equals("ADMIN")
        && Boolean.TRUE.equals(u.getEnabled())
        && (!input.role().equals("ADMIN") || Boolean.FALSE.equals(input.enabled()))
        && activeAdmins.size() <= 1)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cần giữ ít nhất một quản trị viên hoạt động.");
    u.setRole(input.role());
    if (input.enabled() != null) u.setEnabled(input.enabled());
    if (input.password() != null && !input.password().isBlank()) {
      validateCredentials(u.getUsername(), input.password());
      u.setPasswordHash(encoder.encode(input.password()));
    }
    return users.save(u);
  }

  private Map<String, String> identity(Authentication auth) {
    return Map.of(
        "username",
        auth.getName(),
        "role",
        auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
  }

  private void validateCredentials(String username, String password) {
    if (username == null
        || !username.matches("[A-Za-z0-9_.-]{3,80}")
        || password == null
        || password.length() < 10
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Tên đăng nhập 3–80 ký tự (chữ, số, _, ., -); mật khẩu 10–72 ký tự.");
  }

  private void validateRole(String role) {
    if (role == null || !Set.of("ADMIN", "OPERATOR", "VIEWER").contains(role))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ.");
  }

  public record Credentials(String username, String password) {
    @Override
    public String toString() {
      return "Credentials[username=" + username + ", password=***]";
    }
  }

  public record UserInput(String username, String password, String role, Boolean enabled) {
    @Override
    public String toString() {
      return "UserInput[username=" + username + ", role=" + role + ", password=***]";
    }
  }
}
