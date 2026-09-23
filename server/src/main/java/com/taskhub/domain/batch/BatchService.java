package com.taskhub.domain.batch;

import com.taskhub.api.*;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.order.OrderService;
import com.taskhub.infrastructure.idempotency.IdempotencyService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BatchService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final OrderService orders;
  private final IdempotencyService idem;
  private final AuditService audit;

  public BatchService(
      JdbcTemplate db,
      AuthorizationService auth,
      OrderService orders,
      IdempotencyService idem,
      AuditService audit) {
    this.db = db;
    this.auth = auth;
    this.orders = orders;
    this.idem = idem;
    this.audit = audit;
  }

  public static ApiException bad(String m) {
    return new ApiException(400, 40000, m);
  }

  public static ApiException conflict(String m) {
    return new ApiException(409, 40903, m);
  }

  private void readScope(Actor actor, String warehouse) {
    if (!Set.of("warehouse", "dispatch").contains(String.valueOf(actor.workspace())))
      throw AuthorizationService.denied();
    var grant = auth.requireWorkspace(actor, actor.workspace());
    if (!AuthorizationService.covers(grant.scope(), grant.warehouseIds(), warehouse))
      throw new ApiException(404, 40400, "批次不存在或不在权限范围");
  }

  public Map<String, Object> row(String id, boolean lock) {
    var rows = db.queryForList("SELECT * FROM batch WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new ApiException(404, 40400, "批次不存在");
    return rows.get(0);
  }

  public List<String> members(String id) {
    return db.queryForList(
        "SELECT order_id FROM active_order_batch WHERE batch_id=? ORDER BY order_id",
        String.class,
        id);
  }

  public Map<String, Object> get(Actor actor, String id) {
    var row = row(id, false);
    readScope(actor, (String) row.get("warehouse_id"));
    var result = new LinkedHashMap<String, Object>();
    for (var f : List.of("id", "number", "version", "status")) result.put(f, row.get(f));
    for (var f :
        List.of(
            "warehouse_id",
            "stop_id",
            "slot_id",
            "load_status",
            "vehicle_id",
            "dispatch_request_id",
            "task_id")) {
      String[] p = f.split("_");
      String k = p[0];
      for (int i = 1; i < p.length; i++)
        k += Character.toUpperCase(p[i].charAt(0)) + p[i].substring(1);
      result.put(k, row.get(f));
    }
    var ids = members(id);
    result.put("orderIds", ids);
    result.put("orders", ids.stream().map(o -> orders.get(actor, o)).toList());
    result.put(
        "warehouseName",
        db.queryForObject(
            "SELECT name FROM warehouse WHERE id=?", String.class, row.get("warehouse_id")));
    result.put(
        "stopName",
        db.queryForObject("SELECT name FROM stop WHERE id=?", String.class, row.get("stop_id")));
    result.put(
        "assignments",
        ids.stream()
            .map(
                o ->
                    Map.of(
                        "orderId",
                        o,
                        "compartmentIds",
                        db.queryForList(
                            "SELECT compartment_id FROM batch_assignment WHERE batch_id=? AND"
                                + " order_id=? ORDER BY compartment_id",
                            String.class,
                            id,
                            o)))
            .toList());
    result.put(
        "allowedActions",
        "warehouse".equals(actor.workspace())
                && Set.of("DRAFT", "READY").contains(row.get("status"))
            ? List.of("BATCH_EDIT", "LOAD_ASSIGN", "LOAD_CONFIRM")
            : List.of());
    return result;
  }

  public PageResponse<Map<String, Object>> list(
      Actor actor, String warehouse, String status, int page, int size) {
    if (!Set.of("warehouse", "dispatch").contains(String.valueOf(actor.workspace())))
      throw AuthorizationService.denied();
    var grant = auth.requireWorkspace(actor, actor.workspace());
    if (page < 1 || page > 100000 || size < 1 || size > 100) throw bad("分页参数无效");
    var args = new ArrayList<Object>();
    String where = "1=1";
    if (!"ALL".equals(grant.scope())) {
      if (grant.warehouseIds().isEmpty()) return new PageResponse<>(List.of(), 0, page, size);
      where +=
          " AND warehouse_id IN ("
              + String.join(",", Collections.nCopies(grant.warehouseIds().size(), "?"))
              + ")";
      args.addAll(grant.warehouseIds());
    }
    if (warehouse != null && !warehouse.isBlank()) {
      where += " AND warehouse_id=?";
      args.add(warehouse);
    }
    if (status != null && !status.isBlank()) {
      where += " AND status=?";
      args.add(status);
    }
    int total =
        db.queryForObject(
            "SELECT COUNT(*) FROM batch WHERE " + where, Integer.class, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var ids =
        db.queryForList(
            "SELECT id FROM batch WHERE " + where + " ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
            String.class,
            args.toArray());
    return new PageResponse<>(ids.stream().map(id -> get(actor, id)).toList(), total, page, size);
  }

  private List<String> ids(List<String> ids) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 100
        || ids.stream().anyMatch(i -> i == null || i.isBlank())
        || new HashSet<>(ids).size() != ids.size()) throw bad("批次需要1至100个不重复订单");
    return ids.stream().sorted().toList();
  }

  private List<Map<String, Object>> lockOrders(List<String> ids) {
    return ids.stream()
        .sorted()
        .map(
            id -> {
              var rows = db.queryForList("SELECT * FROM delivery_order WHERE id=? FOR UPDATE", id);
              if (rows.isEmpty()) throw new ApiException(404, 40400, "订单不存在");
              return rows.get(0);
            })
        .toList();
  }

  private void validate(
      Actor actor, List<Map<String, Object>> rows, String batch, Map<String, Object> reference) {
    for (var order : rows) {
      auth.require(actor, "BATCH_MANAGE", (String) order.get("warehouse_id"));
      for (String key : List.of("warehouse_id", "stop_id", "slot_id"))
        if (!Objects.equals(order.get(key), reference.get(key)))
          throw new ApiException(422, 42203, "仅同仓库、同目的地、同预约时段可组批");
      var existing =
          db.queryForList(
              "SELECT batch_id FROM active_order_batch WHERE order_id=?",
              String.class,
              order.get("id"));
      if (!existing.isEmpty() && !existing.get(0).equals(batch)) throw conflict("订单已加入其他活动批次");
      if (!"ACCEPTED".equals(order.get("status"))
          && !("READY".equals(order.get("status"))
              && !existing.isEmpty()
              && existing.get(0).equals(batch))) throw conflict("只有受理订单可加入批次");
      if (db.queryForObject(
              "SELECT COUNT(*) FROM order_cancellation WHERE order_id=? AND status='PENDING'",
              Integer.class,
              order.get("id"))
          > 0) throw conflict("订单取消申请待处理");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> create(Actor actor, String key, BatchModels.Create input) {
    auth.requireWorkspace(actor, "warehouse");
    var ids = ids(input.orderIds());
    return idem.execute(
        actor,
        "POST /batches",
        key,
        input,
        Map.class,
        () -> {
          var rows = lockOrders(ids);
          var first = rows.get(0);
          validate(actor, rows, null, first);
          String id = UUID.randomUUID().toString();
          db.update(
              "INSERT INTO batch(id,number,warehouse_id,stop_id,slot_id) VALUES(?,?,?,?,?)",
              id,
              "B" + id.replace("-", ""),
              first.get("warehouse_id"),
              first.get("stop_id"),
              first.get("slot_id"));
          for (String order : ids) add(id, order);
          var result = get(actor, id);
          audit.record(actor, "BATCH", id, "BATCH_CREATE", null, result, null);
          return result;
        });
  }

  private void add(String batch, String order) {
    db.update("INSERT INTO active_order_batch(order_id,batch_id) VALUES(?,?)", order, batch);
    db.update(
        "INSERT INTO batch_order(id,batch_id,order_id) VALUES(?,?,?)",
        UUID.randomUUID().toString(),
        batch,
        order);
    db.update(
        "UPDATE delivery_order SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
        order);
  }

  public void writable(Actor actor, Map<String, Object> row, Integer version) {
    auth.require(actor, "BATCH_MANAGE", (String) row.get("warehouse_id"));
    if (version == null) throw bad("expectedVersion必填");
    if (version.intValue() != ((Number) row.get("version")).intValue())
      throw new ApiException(409, 40902, "批次版本已变化，请刷新");
    if (!Set.of("DRAFT", "READY").contains(row.get("status"))) throw conflict("批次已锁定或关闭");
  }

  public void invalidate(String id) {
    db.update(
        "UPDATE delivery_order SET status='ACCEPTED',version=version+1,updated_at=UTC_TIMESTAMP(3)"
            + " WHERE status='READY' AND id IN (SELECT order_id FROM active_order_batch WHERE"
            + " batch_id=?)",
        id);
    db.update("DELETE FROM batch_assignment WHERE batch_id=?", id);
    db.update("DELETE FROM active_compartment WHERE batch_id=?", id);
    db.update(
        "UPDATE batch SET"
            + " status='DRAFT',load_status='UNCONFIRMED',vehicle_id=NULL,confirmed_by=NULL,confirmed_at=NULL,confirmed_version=NULL,assignments_hash=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3)"
            + " WHERE id=?",
        id);
  }

  private void remove(String id, String order) {
    db.update("DELETE FROM active_order_batch WHERE batch_id=? AND order_id=?", id, order);
    db.update(
        "UPDATE batch_order SET removed_at=UTC_TIMESTAMP(3) WHERE batch_id=? AND order_id=? AND"
            + " removed_at IS NULL",
        id,
        order);
    db.update(
        "UPDATE delivery_order SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
        order);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> replace(
      Actor actor, String id, String key, BatchModels.Members input) {
    auth.requireWorkspace(actor, "warehouse");
    get(actor, id);
    var ids = ids(input.orderIds());
    return idem.execute(
        actor,
        "PUT /batches/" + id + "/orders",
        key,
        input,
        Map.class,
        () -> {
          var row = row(id, true);
          writable(actor, row, input.expectedVersion());
          var old = members(id);
          var union = new TreeSet<>(old);
          union.addAll(ids);
          var rows =
              lockOrders(new ArrayList<>(union)).stream()
                  .filter(order -> ids.contains(order.get("id")))
                  .toList();
          validate(actor, rows, id, row);
          var before = get(actor, id);
          invalidate(id);
          for (String order : old) if (!ids.contains(order)) remove(id, order);
          for (String order : ids) if (!old.contains(order)) add(id, order);
          var result = get(actor, id);
          audit.record(actor, "BATCH", id, "BATCH_MEMBERS", before, result, null);
          return result;
        });
  }

  // 取消审核调用前锁批次，随后才锁订单；组批事务只持有订单锁，不等待已有批次。
  public String lockForCancellation(String order) {
    var ids =
        db.queryForList(
            "SELECT batch_id FROM active_order_batch WHERE order_id=?", String.class, order);
    if (ids.isEmpty()) return null;
    row(ids.get(0), true);
    lockOrders(members(ids.get(0)));
    return ids.get(0);
  }

  public void removeForCancellation(Actor actor, String order, String lockedBatch) {
    var active =
        db.queryForList(
            "SELECT batch_id FROM active_order_batch WHERE order_id=?", String.class, order);
    if (active.isEmpty()) return;
    if (!active.get(0).equals(lockedBatch)) throw conflict("订单批次已变化，请重试取消审核");
    String id = active.get(0);
    var row = row(id, false);
    if (!Set.of("DRAFT", "READY").contains(row.get("status")))
      throw conflict("派发未决或已派发，请先现场核对；取消申请仍待处理");
    var before = get(actor, id);
    lockOrders(members(id));
    invalidate(id);
    remove(id, order);
    if (members(id).isEmpty()) db.update("UPDATE batch SET status='CLOSED' WHERE id=?", id);
    audit.record(
        actor, "BATCH", id, "BATCH_CANCEL_REMOVE", before, get(actor, id), "订单取消安全移出；空批显式关闭");
  }
}
