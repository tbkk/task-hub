package com.taskhub.domain.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.ApiException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class SimulatorVehicleGateway implements VehicleGateway {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final TransactionTemplate transaction;

  public SimulatorVehicleGateway(
      JdbcTemplate db, ObjectMapper json, PlatformTransactionManager manager) {
    this.db = db;
    this.json = json;
    this.transaction = new TransactionTemplate(manager);
  }

  public GatewayResult dispatch(DispatchCommand c) {
    required(c.fromStopId());
    identifiers(c.goalStopIds());
    return execute("DISPATCH", c.requestId(), c.vehicleId(), null, c);
  }

  public GatewayResult open(OpenCommand c) {
    identifiers(c.hardwareNos());
    return execute("OPEN", c.requestId(), c.vehicleId(), c.dispatchId(), c);
  }

  public GatewayResult go(TaskCommand c) {
    required(c.dispatchId());
    return execute("GO", c.requestId(), c.vehicleId(), c.dispatchId(), c);
  }

  public GatewayResult cancel(TaskCommand c) {
    required(c.dispatchId());
    return execute("CANCEL", c.requestId(), c.vehicleId(), c.dispatchId(), c);
  }

  public GatewayResult queryCommand(String requestId) {
    required(requestId);
    var rows =
        db.queryForList("SELECT result_json FROM simulator_command WHERE request_id=?", requestId);
    if (rows.isEmpty()) return new GatewayResult(GatewayStatus.UNKNOWN, null, "模拟请求尚无可核对结果");
    return read((String) rows.get(0).get("result_json"));
  }

  @Override
  public GatewayResult queryDispatch(DispatchCommand command, java.time.Instant requestedAt) {
    var rows =
        db.queryForList(
            "SELECT command_json,result_json,created_at,operation FROM simulator_command WHERE"
                + " request_id=?",
            command.requestId());
    if (rows.isEmpty()) return new GatewayResult(GatewayStatus.UNKNOWN, null, "没有对应派发记录");
    var row = rows.get(0);
    try {
      boolean matches =
          "DISPATCH".equals(row.get("operation"))
              && json.readTree((String) row.get("command_json")).equals(json.valueToTree(command))
              && !com.taskhub.infrastructure.DatabaseTime.instant(row.get("created_at"))
                  .isBefore(requestedAt);
      if (!matches) return new GatewayResult(GatewayStatus.UNKNOWN, null, "车辆、目标或请求时间不匹配，需要人工核对");
      return read((String) row.get("result_json"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private GatewayResult execute(
      String operation, String request, String vehicle, String dispatchId, Object command) {
    required(request);
    required(vehicle);
    String body = write(command);
    return transaction.execute(
        status -> {
          db.update(
              "INSERT IGNORE INTO simulator_command(request_id,operation,command_json)"
                  + " VALUES(?,?,?)",
              request,
              operation,
              body);
          var row =
              db.queryForMap(
                  "SELECT operation,command_json,result_json FROM simulator_command WHERE"
                      + " request_id=? FOR UPDATE",
                  request);
          try {
            if (!operation.equals(row.get("operation"))
                || !json.readTree(body).equals(json.readTree((String) row.get("command_json"))))
              throw new ApiException(409, 40901, "模拟请求标识已用于不同命令");
          } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
          }
          if (row.get("result_json") != null) return read((String) row.get("result_json"));
          var scenarios =
              db.queryForList(
                  "SELECT outcome FROM simulator_scenario WHERE operation=?", operation);
          String outcome =
              scenarios.isEmpty() ? "ACCEPTED" : (String) scenarios.get(0).get("outcome");
          GatewayStatus resultStatus =
              switch (outcome) {
                case "ACCEPTED" -> GatewayStatus.ACCEPTED;
                case "FAILED" -> GatewayStatus.FAILED;
                default -> GatewayStatus.UNKNOWN;
              };
          String externalId =
              operation.equals("DISPATCH") && resultStatus == GatewayStatus.ACCEPTED
                  ? "sim-" + UUID.randomUUID()
                  : dispatchId;
          var result =
              new GatewayResult(
                  resultStatus,
                  externalId,
                  switch (resultStatus) {
                    case ACCEPTED -> "模拟请求已受理，等待现场状态";
                    case FAILED -> "模拟提供者拒绝请求";
                    case UNKNOWN -> "模拟响应超时，结果待核对";
                  });
          db.update(
              "UPDATE simulator_command SET result_json=? WHERE request_id=?",
              write(result),
              request);
          return result;
        });
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private GatewayResult read(String value) {
    if (value == null) return new GatewayResult(GatewayStatus.UNKNOWN, null, "模拟请求结果待核对");
    try {
      return json.readValue(value, GatewayResult.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void required(String value) {
    if (value == null || value.isBlank() || value.length() > 128)
      throw new ApiException(400, 40000, "车辆命令标识无效");
  }

  private static void identifiers(List<String> values) {
    if (values == null
        || values.isEmpty()
        || values.size() > 100
        || new HashSet<>(values).size() != values.size())
      throw new ApiException(422, 42204, "必须指定非空且不重复的目标");
    values.forEach(SimulatorVehicleGateway::required);
  }
}
