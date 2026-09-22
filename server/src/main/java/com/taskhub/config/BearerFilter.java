package com.taskhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.*;
import com.taskhub.domain.identity.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerFilter extends OncePerRequestFilter {
  private final SessionService sessions;
  private final ObjectMapper json;

  public BearerFilter(SessionService sessions, ObjectMapper json) {
    this.sessions = sessions;
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    String path = request.getRequestURI().substring(request.getContextPath().length());
    String header = request.getHeader("Authorization");
    boolean publicPath =
        List.of(
                "/api/health",
                "/api/ready",
                "/api/admin/auth/login",
                "/api/mini/auth/wechat",
                "/api/mini/auth/sms",
                "/api/mini/auth/verify",
                "/api/identity/logout")
            .contains(path);
    if (!publicPath && header != null) {
      try {
        if (!header.startsWith("Bearer ")) throw SessionService.invalid();
        var actor =
            sessions
                .authenticate(header.substring(7))
                .withWorkspace(request.getHeader("X-Workspace"));
        if (actor.identity().mustChangePassword()
            && !List.of("/api/identity/me", "/api/identity/password").contains(path))
          throw new ApiException(403, 40302, "请先修改临时密码");
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
      } catch (ApiException e) {
        response.setStatus(e.status());
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.error(e.code(), e.getMessage()));
        return;
      } catch (org.springframework.dao.DataAccessException e) {
        response.setStatus(503);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.error(50300, "认证服务暂不可用"));
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
