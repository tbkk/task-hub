package com.taskhub.api;

import com.taskhub.infrastructure.metadata.ApplicationMetadataMapper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemStatusController {

    private final ApplicationMetadataMapper metadataMapper;

    public SystemStatusController(ApplicationMetadataMapper metadataMapper) {
        this.metadataMapper = metadataMapper;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<?>> ready() {
        try {
            String schemaVersion = metadataMapper.findValue("schema_version");
            if (schemaVersion == null) {
                return unavailable();
            }
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "READY",
                "schemaVersion", schemaVersion
            )));
        } catch (RuntimeException exception) {
            return unavailable();
        }
    }

    private ResponseEntity<ApiResponse<?>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(503, "service unavailable"));
    }
}
