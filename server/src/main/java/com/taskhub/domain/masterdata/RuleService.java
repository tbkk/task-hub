package com.taskhub.domain.masterdata;

import static com.taskhub.domain.masterdata.MasterdataAccess.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.time.LocalTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleService {
  private final MasterdataMapper db;
  private final MasterdataAccess access;
  private final ObjectMapper json;

  public RuleService(MasterdataMapper db, MasterdataAccess access, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.json = json;
  }

  public com.taskhub.api.PageResponse<Warehouse> warehouses(Actor actor, int page, int size) {
    var grant = access.grant(actor, "RULE_MANAGE");
    return MasterdataAccess.page(
        db.warehouses().stream().filter(w -> access.covers(grant, w.id())).toList(), page, size);
  }

  public Rule read(String warehouse) {
    var row = found(db.rule(warehouse));
    try {
      return copy(json.readValue(row.config(), Rule.class), row.version());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("规则存储格式无效", e);
    }
  }

  public Rule get(Actor actor, String warehouse) {
    access.require(actor, "RULE_MANAGE", warehouse);
    found(db.warehouse(warehouse));
    return read(warehouse);
  }

  private Rule copy(Rule r, int version) {
    return new Rule(
        version,
        r.businessHours(),
        r.slotCapacity(),
        r.bookingDays(),
        r.descriptionMaxLength(),
        r.sizeMaxLength(),
        r.remarkMaxLength(),
        r.telemetryMaxAgeSeconds(),
        r.doorMaxAgeSeconds(),
        r.pickupTimeoutMinutes(),
        r.sharedCompartmentEnabled(),
        null);
  }

  private void positive(Integer v, int max) {
    if (v == null || v < 1 || v > max) throw bad("规则数值超出允许范围");
  }

  private int minute(String v) {
    if (v == null || !v.matches("(?:[01][0-9]|2[0-3]):(?:00|30)")) throw bad("营业时间须使用半小时刻度 HH:mm");
    var t = LocalTime.parse(v);
    return t.getHour() * 60 + t.getMinute();
  }

  private void validate(Rule r) {
    positive(r.slotCapacity(), 10000);
    positive(r.bookingDays(), 365);
    positive(r.descriptionMaxLength(), 10000);
    positive(r.sizeMaxLength(), 10000);
    positive(r.remarkMaxLength(), 10000);
    positive(r.telemetryMaxAgeSeconds(), 86400);
    positive(r.doorMaxAgeSeconds(), 86400);
    positive(r.pickupTimeoutMinutes(), 1440);
    if (!Boolean.FALSE.equals(r.sharedCompartmentEnabled())) throw bad("共享格口尚未开放");
    if (r.businessHours() == null || r.businessHours().size() > 100) throw bad("营业时段无效");
    var days = new HashMap<Integer, List<int[]>>();
    for (var h : r.businessHours()) {
      if (h == null || h.weekday() == null || h.weekday() < 1 || h.weekday() > 7)
        throw bad("星期须为 1 至 7");
      int start = minute(h.start()), end = minute(h.end());
      if (start >= end) throw bad("营业结束时间须晚于开始时间");
      var intervals = days.computeIfAbsent(h.weekday(), k -> new ArrayList<>());
      for (var interval : intervals)
        if (start < interval[1] && end > interval[0]) throw bad("营业时段不能重叠");
      intervals.add(new int[] {start, end});
    }
  }

  @Transactional
  public Rule update(Actor actor, String warehouse, Rule input) {
    access.lock(actor);
    access.require(actor, "RULE_MANAGE", warehouse);
    found(db.lockWarehouse(warehouse));
    validate(input);
    var old = db.rule(warehouse);
    version(input.expectedVersion(), old == null ? 0 : old.version());
    try {
      String value = json.writeValueAsString(copy(input, 0));
      if (old == null) {
        db.insertRule(warehouse, value);
        db.updateRule(warehouse, value, 0);
      } else if (db.updateRule(warehouse, value, old.version()) != 1) throw conflict();
      return read(warehouse);
    } catch (JsonProcessingException e) {
      throw bad("规则格式无效");
    }
  }
}
