package com.taskhub.vehicle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.taskhub.domain.vehicle.ControlPolicy;

class ControlPolicyTest {
  @Test
  void goBlocksUnknownDoorAndMissingNextStop() {
    var c = new ControlPolicy.Context("AT_STOP", "ATSTOP", "s1", null, "s1", true, true,
        BigDecimal.ZERO, List.of("COMPLETED"), List.of("UNKNOWN"), List.of(false), false, false);
    var codes = ControlPolicy.blockers(c, true).stream().map(ControlPolicy.Blocker::code).toList();
    assertTrue(codes.contains("DOOR_UNKNOWN"));
    assertTrue(codes.contains("NO_NEXT_STOP"));
  }

  @Test
  void cancelBlocksUnknownOrMovingSpeed() {
    var c = new ControlPolicy.Context("AT_STOP", "ATSTOP", "s1", "s2", "s1", true, true,
        null, List.of("COMPLETED"), List.of("CLOSED"), List.of(true), false, false);
    assertTrue(ControlPolicy.blockers(c, false).stream().anyMatch(b -> b.code().equals("VEHICLE_MOVING")));
  }
}
