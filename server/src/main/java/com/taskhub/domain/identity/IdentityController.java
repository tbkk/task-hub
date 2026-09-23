package com.taskhub.domain.identity;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class IdentityController {
  private final AdminAuthService auth;
  private final SessionService sessions;

  public IdentityController(AdminAuthService auth, SessionService sessions) {
    this.auth = auth;
    this.sessions = sessions;
  }

  @PostMapping("/admin/auth/login")
  public ApiResponse<Session> login(@RequestBody LoginInput input, HttpServletRequest request) {
    return ApiResponse.success(auth.login(input, request.getRemoteAddr()));
  }

  @GetMapping("/identity/me")
  public ApiResponse<Identity> me(@AuthenticationPrincipal Actor actor) {
    return ApiResponse.success(sessions.identity(actor.employeeId()));
  }

  @PostMapping("/identity/logout")
  public ApiResponse<Void> logout(
      @RequestHeader(value = "Authorization", required = false) String header) {
    if (header != null && header.startsWith("Bearer ")) sessions.revokeToken(header.substring(7));
    return ApiResponse.success(null);
  }

  @PostMapping("/identity/password")
  public ApiResponse<Void> password(
      @AuthenticationPrincipal Actor actor, @RequestBody PasswordInput input) {
    auth.changePassword(actor, input);
    return ApiResponse.success(null);
  }
}
