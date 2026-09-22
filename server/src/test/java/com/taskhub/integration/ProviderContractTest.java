package com.taskhub.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.domain.integration.JiushiMqttAdapter;
import org.junit.jupiter.api.Test;

class ProviderContractTest {
  @Test
  void parsesBusinessPushWithoutInventingMissingTelemetry() {
    var event = new JiushiMqttAdapter(new ObjectMapper()).parse(
        "org/vehicle/V1/business/push",
        "{\"vehicleName\":\"V1\",\"timestamp\":1790035200000,\"vehicleBusinessStatus\":\"ATSTOP\",\"currentStopId\":\"S1\"}");
    assertEquals("V1", event.vehicleId());
    assertEquals("ATSTOP", event.businessStatus());
    assertNull(event.speed());
    assertNull(event.nextStopId());
  }

  @Test
  void rejectsMessagesWithoutVehicleIdentity() {
    assertThrows(IllegalArgumentException.class, () -> new JiushiMqttAdapter(new ObjectMapper()).parse("x", "{}"));
  }
}
