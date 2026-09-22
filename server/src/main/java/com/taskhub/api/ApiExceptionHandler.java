package com.taskhub.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiResponse<Void>> business(ApiException error) {
    return ResponseEntity.status(error.status())
        .body(ApiResponse.error(error.code(), error.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiResponse<Void>> invalidJson() {
    return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求内容格式不正确"));
  }

  @ExceptionHandler({
    org.springframework.beans.TypeMismatchException.class,
    org.springframework.validation.BindException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  ResponseEntity<ApiResponse<Void>> invalidParameters() {
    return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求参数格式不正确"));
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ResponseEntity<ApiResponse<Void>> databaseUnavailable() {
    return ResponseEntity.status(503).body(ApiResponse.error(50300, "服务暂不可用，请稍后重试"));
  }
}
