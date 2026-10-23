package com.mycompany.do_an_ltm_2380600550.controller;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> status(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .body(Map.of("message", ex.getReason() == null ? "Yêu cầu không hợp lệ." : ex.getReason()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<?> invalid(Exception ex) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", "Dữ liệu hoặc tham số không hợp lệ."));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict(Exception ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("message", "Dữ liệu bị trùng hoặc đã thay đổi. Hãy tải lại và thử lại."));
  }
}
