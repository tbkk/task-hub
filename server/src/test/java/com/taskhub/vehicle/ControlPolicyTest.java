package com.taskhub.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.taskhub.domain.vehicle.ControlPolicy;

class ControlPolicyTest {
  private static ControlPolicy.Context context(
      String taskState, String businessStatus, String current, String next, String expected,
      Boolean online, Boolean fresh, BigDecimal speed, List<String> orders, List<String> doors,
      List<Boolean> doorFresh, boolean ticket, boolean unknown) {
    return new ControlPolicy.Context(taskState, businessStatus, current, next, expected, online,
        fresh, speed, orders, doors, doorFresh, ticket, unknown);
  }

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
    var c = context("AT_STOP", "ATSTOP", "s1", "s2", "s1", true, true, null,
        List.of("COMPLETED"), List.of("CLOSED"), List.of(true), false, false);
    assertTrue(ControlPolicy.blockers(c, false).stream().anyMatch(b -> b.code().equals("VEHICLE_MOVING")));
  }

  @Test
  void everyGoBlockerHasStableCode() {
    var c = context("RUNNING", "ONWAY", "wrong", "", "expected", false, false,
        BigDecimal.ONE, List.of("AWAITING_PICKUP"), List.of("OPEN", "UNKNOWN"),
        List.of(true, false), true, true);
    var codes = ControlPolicy.blockers(c, true).stream().map(ControlPolicy.Blocker::code).toList();
    assertEquals(List.of("VEHICLE_STALE", "TASK_MISMATCH", "NO_NEXT_STOP", "DOOR_OPEN",
        "DOOR_UNKNOWN", "ORDER_NOT_PICKED", "ACTIVE_TICKET", "UNKNOWN_CONTROL"), codes);
  }

  @Test
  void cancelDoesNotRequireDoorsOrPickedOrdersButStillRequiresFreshVehicleAndNoTicket() {
    var c = context("AT_STOP", "ATSTOP", "s1", "s2", "s1", true, true, BigDecimal.ZERO,
        List.of("AWAITING_PICKUP"), List.of("OPEN"), List.of(false), true, false);
    var codes = ControlPolicy.blockers(c, false).stream().map(ControlPolicy.Blocker::code).toList();
    assertEquals(List.of("ACTIVE_TICKET"), codes);
  }

  @Test
  void unknownSpeedCannotBeTreatedAsStopped() {
    var c = context("AT_STOP", "ATSTOP", "s1", "s2", "s1", true, true, null,
        List.of(), List.of(), List.of(), false, false);
    assertEquals("VEHICLE_MOVING", ControlPolicy.blockers(c, false).get(0).code());
  }
}
