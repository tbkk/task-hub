package com.taskhub.domain.identity;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Session;
import com.taskhub.domain.identity.MiniAuthModels.*;
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

  @PostMapping("/bind")
  public ApiResponse<Session> bind(@RequestBody BindInput input) {
    return ApiResponse.success(auth.bind(input));
  }
}
