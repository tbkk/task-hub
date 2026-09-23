package com.taskhub.domain.order;
import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.vehicle.ControlModels;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class PickupController {
 private final PickupService service; public PickupController(PickupService service){this.service=service;}
 @PostMapping("/pickup/scan") public ApiResponse<Map<String,Object>> scan(@AuthenticationPrincipal Actor a,@RequestBody ControlModels.Scan i){return ApiResponse.success(service.scan(a,i));}
 @PostMapping("/orders/{id}/pickup/open") public ApiResponse<Map<String,Object>> open(@AuthenticationPrincipal Actor a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody ControlModels.Input i){return ApiResponse.success(service.open(a,id,i,key));}
 @PostMapping("/orders/{id}/pickup/confirm") public ApiResponse<Map<String,Object>> confirm(@AuthenticationPrincipal Actor a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody ControlModels.Input i){return ApiResponse.success(service.confirm(a,id,i,key));}
}
