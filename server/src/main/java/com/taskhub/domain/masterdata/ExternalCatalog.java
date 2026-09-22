package com.taskhub.domain.masterdata;

import com.taskhub.api.ApiException;
import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ExternalCatalog {
  private final MasterdataMapper mapper;
  private final Environment environment;

  public ExternalCatalog(MasterdataMapper mapper, Environment environment) {
    this.mapper = mapper;
    this.environment = environment;
  }

  private void ready() {
    var profiles = List.of(environment.getActiveProfiles());
    if (!(profiles.contains("local") || profiles.contains("test"))
        || profiles.contains("prod")
        || profiles.contains("production")) throw new ApiException(503, 50300, "外部目录尚未配置");
  }

  public List<ExternalResource> list(String resource) {
    ready();
    if (!Set.of("stops", "vehicles").contains(resource)) throw MasterdataAccess.bad("目录类型无效");
    return mapper.external(resource);
  }

  public void require(String resource, String id) {
    if (list(resource).stream()
        .noneMatch(v -> (resource.equals("vehicles") ? v.name() : v.id()).equals(id)))
      throw new ApiException(422, 422, "外部资源不存在，请先同步目录");
  }

  public List<String> hardware(String vehicle) {
    require("vehicles", vehicle);
    return mapper.hardware(vehicle);
  }
}
