package com.taskhub.domain.identity;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Session;
import com.taskhub.domain.identity.MiniAuthModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mini/auth")
public class MiniAuthController {
  private final MiniAuthService auth;

  public MiniAuthController(MiniAuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/wechat")
  public ApiResponse<WechatExchange> wechat(@RequestBody WechatInput input) {
    return ApiResponse.success(auth.exchange(input.code()));
  }

  @PostMapping("/sms")
  public ApiResponse<ChallengeResponse> sms(
      @RequestBody SmsInput input, HttpServletRequest request) {
    return ApiResponse.success(auth.send(input, request.getRemoteAddr()));
  }

  @PostMapping("/verify")
  public ApiResponse<Session> verify(@RequestBody VerifyInput input) {
    return ApiResponse.success(auth.verify(input));
  }
}
