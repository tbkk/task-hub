package com.taskhub.domain.masterdata;

import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  private final MasterdataMapper db;
  private final AuthorizationService auth;
  private final RuleService rules;

  public CatalogService(MasterdataMapper db, AuthorizationService auth, RuleService rules) {
    this.db = db;
    this.auth = auth;
    this.rules = rules;
  }

  public List<Warehouse> warehouses(Actor actor) {
    if (actor.workspace() == null) throw AuthorizationService.denied();
    var grant = auth.requireWorkspace(actor, actor.workspace());
    String home = "worker".equals(grant.role()) ? db.homeWarehouse(actor.employeeId()) : null;
    return db.warehouses().stream()
        .filter(Warehouse::enabled)
        .filter(
            w ->
                "worker".equals(grant.role())
                    ? w.id().equals(home)
                    : AuthorizationService.covers(grant.scope(), grant.warehouseIds(), w.id()))
        .toList();
  }

  private void require(Actor actor, String warehouse) {
    if (warehouses(actor).stream().noneMatch(w -> w.id().equals(warehouse)))
      throw AuthorizationService.denied();
  }

  public List<StopView> stops(Actor actor, String warehouse) {
    require(actor, warehouse);
    Set<String> reachable = new HashSet<>();
    db.vehicles().stream()
        .filter(v -> v.enabled() && v.warehouseId().equals(warehouse))
        .forEach(v -> reachable.addAll(db.vehicleStops(v.id())));
    return db.stops().stream()
        .filter(
            s ->
                s.enabled()
                    && reachable.contains(s.id())
                    && db.stopWarehouses(s.id()).contains(warehouse))
        .map(
            s ->
                new StopView(
                    s.id(),
                    s.name(),
                    s.externalStopId(),
                    List.of(warehouse),
                    s.enabled(),
                    s.version(),
                    s.createdAt(),
                    s.updatedAt()))
        .toList();
  }

  public Rule rules(Actor actor, String warehouse) {
    require(actor, warehouse);
    return rules.read(warehouse);
  }
}
