package com.taskhub.domain.order;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataModels.StopView;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
  private final FavoriteService service;

  public FavoriteController(FavoriteService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<StopView>> list(@AuthenticationPrincipal Actor actor) {
    return ApiResponse.success(service.list(actor));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> put(@AuthenticationPrincipal Actor actor, @PathVariable String id) {
    service.put(actor, id);
    return ApiResponse.success(null);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> remove(@AuthenticationPrincipal Actor actor, @PathVariable String id) {
    service.remove(actor, id);
    return ApiResponse.success(null);
  }
}
