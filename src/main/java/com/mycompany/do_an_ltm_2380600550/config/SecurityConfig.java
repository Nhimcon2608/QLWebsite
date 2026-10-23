package com.mycompany.do_an_ltm_2380600550.config;

import com.mycompany.do_an_ltm_2380600550.repository.AppUserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  java.net.http.HttpClient monitorHttpClient() {
    return java.net.http.HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(AppUserRepository users) {
    return username ->
        users
            .findByUsername(username)
            .map(
                u ->
                    User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles(u.getRole())
                        .disabled(!Boolean.TRUE.equals(u.getEnabled()))
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại."));
  }

  @Bean
  AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
    var provider =
        new org.springframework.security.authentication.dao.DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users)
      throws Exception {
    http.csrf(c -> c.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/error", "/api/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/session")
                    .permitAll()
                    .requestMatchers("/api/session")
                    .authenticated()
                    .requestMatchers("/api/users/**", "/api/notifications/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/**")
                    .authenticated()
                    .requestMatchers("/api/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, ex) -> json(res, 401, "Vui lòng đăng nhập."))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            json(
                                res,
                                403,
                                "Bạn không có quyền thực hiện thao tác này hoặc phiên CSRF đã hết"
                                    + " hạn.")))
        .logout(l -> l.disable())
        .requestCache(c -> c.disable())
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated()) {
                  try {
                    var current = users.loadUserByUsername(authentication.getName());
                    if (!current.isEnabled()) throw new UsernameNotFoundException("disabled");
                    SecurityContextHolder.getContext()
                        .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                current, null, current.getAuthorities()));
                  } catch (UsernameNotFoundException ex) {
                    SecurityContextHolder.clearContext();
                    if (req.getSession(false) != null) req.getSession(false).invalidate();
                  }
                }
                chain.doFilter(req, res);
              }
            },
            AnonymousAuthenticationFilter.class);
    return http.build();
  }

  private static void json(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"message\":\"" + message + "\"}");
  }
}
