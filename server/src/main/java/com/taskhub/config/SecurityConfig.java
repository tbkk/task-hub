package com.taskhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.SessionService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type =
        org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectMapper json, SessionService sessions) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .httpBasic(c -> c.disable())
        .formLogin(c -> c.disable())
        .logout(c -> c.disable())
        .addFilterBefore(
            new BearerFilter(sessions, json), UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/health",
                        "/api/ready",
                        "/api/admin/auth/login",
                        "/api/mini/auth/wechat",
                        "/api/mini/auth/bind",
                        "/api/identity/logout")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (request, response, ex) -> {
                          response.setStatus(401);
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(
                              response.getOutputStream(),
                              ApiResponse.error(40101, "会话已失效，请重新登录"));
                        })
                    .accessDeniedHandler(
                        (request, response, ex) -> {
                          response.setStatus(403);
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(
                              response.getOutputStream(), ApiResponse.error(40302, "当前授权不足"));
                        }))
        .build();
  }
}
