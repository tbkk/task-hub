package com.taskhub.domain.order;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.*;
import com.taskhub.domain.masterdata.MasterdataModels.StopView;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final CatalogService catalog;

  public FavoriteService(JdbcTemplate db, AuthorizationService auth, CatalogService catalog) {
    this.db = db;
    this.auth = auth;
    this.catalog = catalog;
  }

  public List<StopView> list(Actor actor) {
    auth.requireWorkspace(actor, "worker");
    var selected =
        db.queryForList(
            "SELECT stop_id FROM favorite_stop WHERE employee_id=?",
            String.class,
            actor.employeeId());
    return catalog.warehouses(actor).stream()
        .flatMap(w -> catalog.stops(actor, w.id()).stream())
        .filter(s -> selected.contains(s.id()))
        .toList();
  }

  @Transactional
  public void put(Actor actor, String id) {
    auth.lockActor(actor);
    auth.requireWorkspace(actor, "worker");
    boolean available =
        catalog.warehouses(actor).stream()
            .flatMap(w -> catalog.stops(actor, w.id()).stream())
            .anyMatch(s -> s.id().equals(id));
    if (!available) throw new ApiException(422, 42201, "点位不可用，不能收藏");
    db.update(
        "INSERT IGNORE INTO favorite_stop(employee_id,stop_id) VALUES(?,?)",
        actor.employeeId(),
        id);
  }

  @Transactional
  public void remove(Actor actor, String id) {
    auth.lockActor(actor);
    auth.requireWorkspace(actor, "worker");
    db.update(
        "DELETE FROM favorite_stop WHERE employee_id=? AND stop_id=?", actor.employeeId(), id);
  }
}
