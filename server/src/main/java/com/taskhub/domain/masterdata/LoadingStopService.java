package com.taskhub.domain.masterdata;

import com.taskhub.api.ApiException;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class LoadingStopService implements MasterdataUsage {
  public record Input(String stopId, Integer expectedVersion) {}

  public record Binding(String id, String stopId, int version) {}

  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final AuditService audit;

  public LoadingStopService(JdbcTemplate db, AuthorizationService auth, AuditService audit) {
    this.db = db;
    this.auth = auth;
    this.audit = audit;
  }

  public Binding get(Actor actor, String warehouse) {
    auth.requirePlatform(actor, "MASTERDATA_MANAGE", warehouse);
    if (db.queryForList("SELECT id FROM warehouse WHERE id=?", warehouse).isEmpty())
      throw new ApiException(404, 40400, "仓库不存在");
    return binding(warehouse);
  }

  public Binding binding(String warehouse) {
    var rows =
        db.queryForList("SELECT * FROM warehouse_loading_stop WHERE warehouse_id=?", warehouse);
    if (rows.isEmpty()) return null;
    var row = rows.get(0);
    return new Binding(
        warehouse, (String) row.get("stop_id"), ((Number) row.get("version")).intValue());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Binding save(Actor actor, String warehouse, Input input) {
    auth.lockActor(actor);
    auth.requirePlatform(actor, "MASTERDATA_MANAGE", warehouse);
    if (input == null
        || input.stopId() == null
        || input.stopId().isBlank()
        || input.expectedVersion() == null)
      throw new ApiException(400, 40000, "装货点和expectedVersion必填");
    var warehouses =
        db.queryForList("SELECT enabled FROM warehouse WHERE id=? FOR UPDATE", warehouse);
    if (warehouses.isEmpty()) throw new ApiException(404, 40400, "仓库不存在");
    if (!Boolean.TRUE.equals(warehouses.get(0).get("enabled")))
      throw new ApiException(422, 42204, "仓库已停用");
    Binding before = binding(warehouse);
    if (input.expectedVersion() != (before == null ? 0 : before.version()))
      throw new ApiException(409, 40902, "装货点配置已更新");
    var stops = db.queryForList("SELECT enabled FROM stop WHERE id=? FOR UPDATE", input.stopId());
    if (stops.isEmpty()
        || !Boolean.TRUE.equals(stops.get(0).get("enabled"))
        || db.queryForObject(
                "SELECT COUNT(*) FROM stop_warehouse WHERE stop_id=? AND warehouse_id=?",
                Integer.class,
                input.stopId(),
                warehouse)
            != 1) throw new ApiException(422, 42204, "请选择已关联本仓库的启用点位");
    if (before != null
        && !Objects.equals(before.stopId(), input.stopId())
        && db.queryForObject(
                "SELECT COUNT(*) FROM batch WHERE warehouse_id=? AND status<>'CLOSED'",
                Integer.class,
                warehouse)
            > 0) throw new ApiException(409, 40903, "仓库有活动批次，暂不能更换装货点");
    if (before == null)
      db.update(
          "INSERT INTO warehouse_loading_stop(warehouse_id,stop_id) VALUES(?,?)",
          warehouse,
          input.stopId());
    else
      db.update(
          "UPDATE warehouse_loading_stop SET stop_id=?,version=version+1 WHERE warehouse_id=?",
          input.stopId(),
          warehouse);
    var result = binding(warehouse);
    audit.record(actor, "WAREHOUSE", warehouse, "LOADING_STOP_CONFIGURE", before, result, null);
    return result;
  }

  @Override
  public void requireChangeAllowed(String resource, String id) {
    if ("stop".equals(resource)
        && db.queryForObject(
                "SELECT COUNT(*) FROM warehouse_loading_stop WHERE stop_id=?", Integer.class, id)
            > 0) throw new ApiException(409, 40903, "点位正在用作仓库装货点，请先调整绑定");
  }
}
