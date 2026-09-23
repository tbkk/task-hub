package com.taskhub.domain.masterdata;

import static com.taskhub.domain.masterdata.MasterdataAccess.*;

import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompartmentService {
  private final MasterdataService resources;

  private final MasterdataMapper db;
  private final MasterdataAccess access;
  private final ExternalCatalog external;

  public CompartmentService(
      MasterdataService resources,
      MasterdataMapper db,
      MasterdataAccess access,
      ExternalCatalog external) {
    this.resources = resources;
    this.db = db;
    this.access = access;
    this.external = external;
  }

  public List<Compartment> list(Actor actor, String vehicle) {
    resources.authorizedVehicle(actor, vehicle);
    return db.compartments(vehicle);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public List<Compartment> sync(Actor actor, String vehicle, VersionInput input) {
    access.lock(actor);
    resources.authorizedVehicle(actor, vehicle);
    var v = found(db.lockVehicle(vehicle));
    access.require(actor, "MASTERDATA_MANAGE", v.warehouseId());
    version(input.expectedVersion(), v.version());
    var hardware = external.hardware(v.externalVehicleName());
    var current = db.compartments(vehicle);
    for (var c : current)
      if (!hardware.contains(c.hardwareNo()) && c.enabled()) {
        resources.change("compartment", c.id());
        db.updateCompartment(c.id(), new CompartmentInput(c.label(), false, c.version()));
      }
    for (String no : hardware)
      if (current.stream().noneMatch(c -> c.hardwareNo().equals(no)))
        db.insertCompartment(UUID.randomUUID().toString(), vehicle, no);
    db.bumpVehicle(vehicle, v.version());
    return db.compartments(vehicle);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Compartment update(Actor actor, String vehicle, String id, CompartmentInput input) {
    access.lock(actor);
    resources.authorizedVehicle(actor, vehicle);
    var vehicleRow = found(db.lockVehicle(vehicle));
    access.require(actor, "MASTERDATA_MANAGE", vehicleRow.warehouseId());
    var c = found(db.lockCompartment(id));
    if (!c.vehicleId().equals(vehicle)) throw new com.taskhub.api.ApiException(404, 404, "格口不存在");
    text(input.label(), 80);
    enabled(input.enabled());
    version(input.expectedVersion(), c.version());
    if (input.enabled()
        && !external.hardware(db.vehicle(vehicle).externalVehicleName()).contains(c.hardwareNo()))
      throw new com.taskhub.api.ApiException(422, 422, "外部硬件定义已不存在");
    if (!input.enabled()) resources.change("compartment", id);
    db.updateCompartment(id, input);
    return db.lockCompartment(id);
  }
}
