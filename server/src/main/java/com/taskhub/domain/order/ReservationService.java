package com.taskhub.domain.order;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public record Slot(String id, Instant start, Instant end, int remaining, boolean available) {}

  private final JdbcTemplate db;
  private final CatalogService catalog;

  public ReservationService(JdbcTemplate db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  public List<Slot> list(Actor actor, String warehouse, String stop, LocalDate date) {
    if (catalog.stops(actor, warehouse).stream().noneMatch(s -> s.id().equals(stop)))
      throw new ApiException(422, 42201, "目的地不可用");
    var rule = catalog.rules(actor, warehouse);
    LocalDate today = LocalDate.now(ZONE);
    if (date.isBefore(today) || !date.isBefore(today.plusDays(rule.bookingDays())))
      throw new ApiException(422, 42201, "预约日期不在可用范围");
    List<Slot> result = new ArrayList<>();
    for (var hour : rule.businessHours())
      if (hour.weekday() == date.getDayOfWeek().getValue()) {
        LocalDateTime end = date.atTime(LocalTime.parse(hour.end()));
        for (LocalDateTime start = date.atTime(LocalTime.parse(hour.start()));
            !start.plusMinutes(30).isAfter(end);
            start = start.plusMinutes(30)) {
          Instant at = start.atZone(ZONE).toInstant();
          if (!at.isAfter(Instant.now())) continue;
          String id =
              UUID.nameUUIDFromBytes((warehouse + ":" + at).getBytes(StandardCharsets.UTF_8))
                  .toString();
          db.update(
              "INSERT IGNORE INTO reservation_slot(id,warehouse_id,start_at,end_at)"
                  + " VALUES(?,?,?,?)",
              id,
              warehouse,
              Timestamp.from(at),
              Timestamp.from(at.plusSeconds(1800)));
          int used =
              db.queryForObject("SELECT used FROM reservation_slot WHERE id=?", Integer.class, id);
          int remaining = Math.max(0, rule.slotCapacity() - used);
          result.add(new Slot(id, at, at.plusSeconds(1800), remaining, remaining > 0));
        }
      }
    result.sort(Comparator.comparing(Slot::start));
    return result;
  }

  public Slot reserve(Actor actor, String warehouse, String stop, String id) {
    var rows =
        db.queryForList(
            "SELECT start_at FROM reservation_slot WHERE id=? AND warehouse_id=?", id, warehouse);
    if (rows.isEmpty()) throw new ApiException(422, 42201, "预约时段不存在");
    Instant at = com.taskhub.infrastructure.DatabaseTime.instant(rows.get(0).get("start_at"));
    var slot =
        list(actor, warehouse, stop, at.atZone(ZONE).toLocalDate()).stream()
            .filter(s -> s.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new ApiException(422, 42201, "预约时段已失效"));
    int capacity = catalog.rules(actor, warehouse).slotCapacity();
    if (db.update(
            "UPDATE reservation_slot SET used=used+1 WHERE id=? AND used<? AND"
                + " start_at>UTC_TIMESTAMP(3)",
            id,
            capacity)
        != 1) throw new ApiException(422, 42201, "预约时段已满或已过期");
    return slot;
  }
}
