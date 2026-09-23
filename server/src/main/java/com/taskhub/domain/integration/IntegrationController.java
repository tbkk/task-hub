package com.taskhub.domain.integration;
import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/integration")
public class IntegrationController {
  private final IntegrationSettingsService service;
  public IntegrationController(IntegrationSettingsService service){this.service=service;}
  @GetMapping("/settings") public ApiResponse<IntegrationSettingsService.Settings> get(@AuthenticationPrincipal Actor actor){return ApiResponse.success(service.get(actor));}
  @PutMapping("/settings") public ApiResponse<IntegrationSettingsService.Settings> save(@AuthenticationPrincipal Actor actor,@RequestBody IntegrationSettingsService.Input input){return ApiResponse.success(service.save(actor,input));}
}
