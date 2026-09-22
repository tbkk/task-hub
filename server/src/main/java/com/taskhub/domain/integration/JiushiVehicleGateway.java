package com.taskhub.domain.integration;

import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** 九识 HTTP 控制适配器。生产启用前必须补齐租户接口路径和凭证。 */
public final class JiushiVehicleGateway implements VehicleGateway {
  private final RestClient client;
  private final JiushiTokenService tokens;
  public JiushiVehicleGateway(RestClient client, JiushiTokenService tokens) { this.client = client; this.tokens = tokens; }

  public GatewayResult dispatch(DispatchCommand c) { return call("/vehicle/add_dispatch", Map.of("vehicleName", c.vehicleId(), "fromStopId", c.fromStopId(), "toStopIds", c.goalStopIds()), c.requestId(), null); }
  public GatewayResult open(OpenCommand c) { return call("/vehicle/open_box", Map.of("vehicleName", c.vehicleId(), "gridNos", c.hardwareNos()), c.requestId(), c.dispatchId()); }
  public GatewayResult go(TaskCommand c) { return call("/vehicle/go", Map.of("vehicleName", c.vehicleId()), c.requestId(), c.dispatchId()); }
  public GatewayResult cancel(TaskCommand c) { return call("/vehicle/cancel_dispatch", Map.of("vehicleName", c.vehicleId()), c.requestId(), c.dispatchId()); }
  public GatewayResult queryCommand(String requestId) { return new GatewayResult(GatewayStatus.UNKNOWN, null, "九识控制结果需按租户查询接口核对"); }

  @SuppressWarnings("unchecked")
  private GatewayResult call(String path, Map<String,Object> body, String requestId, String dispatchId) {
    try {
      Map<String,Object> env = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).header("token", tokens.token()).body(body).retrieve().body(Map.class);
      if (env == null) return new GatewayResult(GatewayStatus.UNKNOWN, dispatchId, "九识响应为空");
      boolean success = Boolean.TRUE.equals(env.get("success"));
      return new GatewayResult(success ? GatewayStatus.ACCEPTED : GatewayStatus.FAILED, dispatchId, String.valueOf(env.getOrDefault("message", success ? "已受理" : "九识拒绝请求")));
    } catch (RuntimeException ex) { tokens.invalidate(); return new GatewayResult(GatewayStatus.UNKNOWN, dispatchId, "九识请求结果待核对"); }
  }
}
