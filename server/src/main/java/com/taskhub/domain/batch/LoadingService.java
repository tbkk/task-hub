package com.taskhub.domain.batch;

import com.taskhub.api.ApiException;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataUsage;
import com.taskhub.infrastructure.idempotency.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoadingService implements MasterdataUsage {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final BatchService batches;
  private final IdempotencyService idem;
  private final AuditService audit;

  public LoadingService(
      JdbcTemplate db,
      AuthorizationService auth,
      BatchService batches,
      IdempotencyService idem,
      AuditService audit) {
    this.db = db;
    this.auth = auth;
    this.batches = batches;
    this.idem = idem;
    this.audit = audit;
  }

  private static ApiException invalid(String m) {
    return new ApiException(422, 42204, m);
  }

  private void validateMembers(String id, List<String> ids) {
    for (String order : ids) {
      var row = db.queryForMap("SELECT status FROM delivery_order WHERE id=? FOR UPDATE", order);
      if (!Set.of("ACCEPTED", "READY").contains(row.get("status")))
        throw BatchService.conflict("成员状态不允许装货");
      if (db.queryForObject(
              "SELECT COUNT(*) FROM order_cancellation WHERE order_id=? AND status='PENDING'",
              Integer.class,
              order)
          > 0) throw BatchService.conflict("存在待审核取消申请");
    }
  }

  private void vehicle(Map<String, Object> batch, String vehicle) {
    var rows = db.queryForList("SELECT * FROM vehicle WHERE id=? FOR UPDATE", vehicle);
    if (rows.isEmpty()
        || !Boolean.TRUE.equals(rows.get(0).get("enabled"))
        || !Objects.equals(rows.get(0).get("warehouse_id"), batch.get("warehouse_id")))
      throw invalid("车辆不存在、停用或不属于该仓库");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM vehicle_stop WHERE vehicle_id=? AND stop_id=?",
            Integer.class,
            vehicle,
            batch.get("stop_id"))
        == 0) throw invalid("车辆未配置该目的地");
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> assign(Actor actor, String id, String key, BatchModels.Loading input) {
    auth.requireWorkspace(actor, "warehouse");
    batches.get(actor, id);
    return idem.execute(
        actor,
        "PUT /batches/" + id + "/loading",
        key,
        input,
        Map.class,
        () -> {
          var batch = batches.row(id, true);
          batches.writable(actor, batch, input.expectedVersion());
          var ids = batches.members(id);
          validateMembers(id, ids);
          if (input.vehicleId() == null
              || input.assignments() == null
              || input.assignments().isEmpty()) throw invalid("车辆与完整格口分配必填");
          vehicle(batch, input.vehicleId());
          var assignedOrders = new HashSet<String>();
          var compartments = new TreeSet<String>();
          for (var assignment : input.assignments()) {
            if (assignment == null
                || !ids.contains(assignment.orderId())
                || !assignedOrders.add(assignment.orderId())
                || assignment.compartmentIds() == null
                || assignment.compartmentIds().isEmpty()) throw invalid("每条订单必须明确格口且不能重复");
            for (String c : assignment.compartmentIds())
              if (c == null || c.isBlank() || !compartments.add(c)) throw invalid("独立格口不得重复分配");
          }
          if (!assignedOrders.equals(new HashSet<>(ids))) throw invalid("分配必须完整覆盖全部订单");
          for (String c : compartments) {
            var rows = db.queryForList("SELECT * FROM compartment WHERE id=? FOR UPDATE", c);
            if (rows.isEmpty()
                || !Boolean.TRUE.equals(rows.get(0).get("enabled"))
                || !input.vehicleId().equals(rows.get(0).get("vehicle_id")))
              throw invalid("格口不存在、停用或属于其他车辆");
            var occupied =
                db.queryForList(
                    "SELECT batch_id FROM active_compartment WHERE compartment_id=?",
                    String.class,
                    c);
            if (!occupied.isEmpty() && !occupied.get(0).equals(id))
              throw BatchService.conflict("格口已被其他批次占用");
          }
          var before = batches.get(actor, id);
          batches.invalidate(id);
          for (var a : input.assignments())
            for (String c : a.compartmentIds()) {
              db.update(
                  "INSERT INTO batch_assignment(batch_id,order_id,compartment_id) VALUES(?,?,?)",
                  id,
                  a.orderId(),
                  c);
              db.update(
                  "INSERT INTO active_compartment(compartment_id,batch_id) VALUES(?,?)", c, id);
            }
          db.update("UPDATE batch SET vehicle_id=? WHERE id=?", input.vehicleId(), id);
          var result = batches.get(actor, id);
          audit.record(actor, "BATCH", id, "LOAD_ASSIGN", before, result, null);
          return result;
        });
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> confirm(
      Actor actor, String id, String key, BatchModels.Confirm input) {
    auth.requireWorkspace(actor, "warehouse");
    batches.get(actor, id);
    return idem.execute(
        actor,
        "POST /batches/" + id + "/loading/confirm",
        key,
        input,
        Map.class,
        () -> {
          var batch = batches.row(id, true);
          batches.writable(actor, batch, input.expectedVersion());
          if (!Boolean.TRUE.equals(input.capacityConfirmed())) throw invalid("请人工确认装载容量");
          var ids = batches.members(id);
          validateMembers(id, ids);
          if (ids.isEmpty() || batch.get("vehicle_id") == null) throw invalid("尚未完整分配车辆格口");
          vehicle(batch, (String) batch.get("vehicle_id"));
          var assignments =
              db.queryForList(
                  "SELECT order_id,compartment_id FROM batch_assignment WHERE batch_id=? ORDER BY"
                      + " order_id,compartment_id",
                  id);
          var assigned = new HashSet<String>();
          StringBuilder hashInput = new StringBuilder((String) batch.get("vehicle_id"));
          for (var a : assignments) {
            String c = (String) a.get("compartment_id");
            var compartment = db.queryForMap("SELECT * FROM compartment WHERE id=? FOR UPDATE", c);
            if (!Boolean.TRUE.equals(compartment.get("enabled"))
                || !batch.get("vehicle_id").equals(compartment.get("vehicle_id"))
                || db.queryForObject(
                        "SELECT COUNT(*) FROM active_compartment WHERE compartment_id=? AND"
                            + " batch_id=?",
                        Integer.class,
                        c,
                        id)
                    != 1) throw invalid("格口或占用状态已变化");
            assigned.add((String) a.get("order_id"));
            hashInput.append('|').append(a.get("order_id")).append(':').append(c);
          }
          if (!assigned.equals(new HashSet<>(ids))) throw invalid("订单格口分配不完整");
          var before = batches.get(actor, id);
          String hash;
          try {
            hash =
                HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(hashInput.toString().getBytes(StandardCharsets.UTF_8)));
          } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
          }
          db.update(
              "UPDATE batch SET"
                  + " status='READY',load_status='CONFIRMED',confirmed_by=?,confirmed_at=UTC_TIMESTAMP(3),confirmed_version=version+1,assignments_hash=?,version=version+1,updated_at=UTC_TIMESTAMP(3)"
                  + " WHERE id=?",
              actor.employeeId(),
              hash,
              id);
          for (String order : ids)
            db.update(
                "UPDATE delivery_order SET"
                    + " status='READY',version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                order);
          var result = batches.get(actor, id);
          audit.record(actor, "BATCH", id, "LOAD_CONFIRM", before, result, "人工确认容量与装货");
          return result;
        });
  }

  @Override
  public void requireChangeAllowed(String resource, String id) {
    if ("compartment".equals(resource)
        && db.queryForObject(
                "SELECT COUNT(*) FROM active_compartment WHERE compartment_id=?", Integer.class, id)
            > 0) throw BatchService.conflict("格口正被批次占用");
    if ("vehicle".equals(resource)
        && db.queryForObject(
                "SELECT COUNT(*) FROM batch WHERE vehicle_id=? AND status<>'CLOSED'",
                Integer.class,
                id)
            > 0) throw BatchService.conflict("车辆正被批次使用");
  }
}
