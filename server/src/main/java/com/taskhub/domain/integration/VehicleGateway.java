package com.taskhub.domain.integration;

import java.util.List;

/** 外部提供者边界：ACCEPTED 仅表示受理，现场状态须由遥测独立确认。 */
public interface VehicleGateway {
  enum GatewayStatus {
    ACCEPTED,
    FAILED,
    UNKNOWN
  }

  record GatewayResult(GatewayStatus status, String dispatchId, String message) {}

  record DispatchCommand(
      String requestId, String vehicleId, String fromStopId, List<String> goalStopIds) {}

  record OpenCommand(
      String requestId, String vehicleId, String dispatchId, List<String> hardwareNos) {}

  record TaskCommand(String requestId, String vehicleId, String dispatchId) {}

  GatewayResult dispatch(DispatchCommand command);

  GatewayResult open(OpenCommand command);

  GatewayResult go(TaskCommand command);

  GatewayResult cancel(TaskCommand command);

  GatewayResult queryCommand(String requestId);

  /** 派发核对必须同时匹配原始请求、车辆、目标与时间；缺证据时保持未知。 */
  default GatewayResult queryDispatch(DispatchCommand command, java.time.Instant requestedAt) {
    return new GatewayResult(GatewayStatus.UNKNOWN, null, "提供者尚未提供完整派发核对证据");
  }
}
