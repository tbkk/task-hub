package com.taskhub.domain.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

/** MQTT 入站解析器；连接与重连由部署层注入，事件交由 VehicleEventService 持久化判序。 */
public final class JiushiMqttAdapter {
  private final ObjectMapper json;
  public JiushiMqttAdapter(ObjectMapper json) { this.json = json; }
  public VehicleEvent parse(String topic, String payload) {
    try {
      JsonNode n = json.readTree(payload);
      String vehicle = text(n, "vehicleName");
      String eventId = vehicle + ":" + (n.has("timestamp") ? n.get("timestamp").asText() : payload.hashCode());
      Instant at = Instant.ofEpochMilli(n.path("timestamp").asLong());
      String status = n.hasNonNull("vehicleBusinessStatus") ? n.get("vehicleBusinessStatus").asText() : null;
      return new VehicleEvent(eventId, vehicle, n.hasNonNull("dispatchId") ? n.get("dispatchId").asText() : null, at, status,
          n.hasNonNull("currentStopId") ? n.get("currentStopId").asText() : null,
          n.hasNonNull("nextStopId") ? n.get("nextStopId").asText() : null,
          n.has("online") ? n.get("online").asBoolean() : null,
          n.has("speed") ? n.get("speed").decimalValue() : null, List.of(),
          n.has("power") ? n.get("power").decimalValue() : null, null, null, null, null);
    } catch (Exception ex) { throw new IllegalArgumentException("九识 MQTT 消息无效", ex); }
  }
  private static String text(JsonNode n, String key) { String v=n.path(key).asText(null); if(v==null||v.isBlank()) throw new IllegalArgumentException("缺少"+key); return v; }
}
