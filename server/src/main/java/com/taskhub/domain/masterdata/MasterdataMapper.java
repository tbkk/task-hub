package com.taskhub.domain.masterdata;

import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MasterdataMapper {
  @Select("SELECT * FROM warehouse ORDER BY created_at DESC,id DESC")
  List<Warehouse> warehouses();

  @Select("SELECT * FROM warehouse WHERE id=#{id}")
  Warehouse warehouse(String id);

  @Select("SELECT * FROM warehouse WHERE id=#{id} FOR UPDATE")
  Warehouse lockWarehouse(String id);

  @Insert(
      "INSERT INTO warehouse(id,name,code,enabled) VALUES(#{id},#{v.name},#{v.code},#{v.enabled})")
  void insertWarehouse(@Param("id") String id, @Param("v") WarehouseInput v);

  @Update(
      "UPDATE warehouse SET"
          + " name=#{v.name},code=#{v.code},enabled=#{v.enabled},version=version+1,updated_at=UTC_TIMESTAMP(3)"
          + " WHERE id=#{id} AND version=#{v.expectedVersion}")
  int updateWarehouse(@Param("id") String id, @Param("v") WarehouseInput v);

  @Select("SELECT home_warehouse_id FROM employee WHERE id=#{id}")
  String homeWarehouse(String id);

  @Select("SELECT * FROM stop ORDER BY created_at DESC,id DESC")
  List<Stop> stops();

  @Select("SELECT * FROM stop WHERE id=#{id}")
  Stop stop(String id);

  @Select("SELECT * FROM stop WHERE id=#{id} FOR UPDATE")
  Stop lockStop(String id);

  @Select("SELECT warehouse_id FROM stop_warehouse WHERE stop_id=#{id} ORDER BY warehouse_id")
  List<String> stopWarehouses(String id);

  @Insert(
      "INSERT INTO stop(id,name,external_stop_id,enabled)"
          + " VALUES(#{id},#{v.name},#{v.externalStopId},#{v.enabled})")
  void insertStop(@Param("id") String id, @Param("v") StopInput v);

  @Update(
      "UPDATE stop SET"
          + " name=#{v.name},external_stop_id=#{v.externalStopId},enabled=#{v.enabled},version=version+1,updated_at=UTC_TIMESTAMP(3)"
          + " WHERE id=#{id} AND version=#{v.expectedVersion}")
  int updateStop(@Param("id") String id, @Param("v") StopInput v);

  @Delete("DELETE FROM stop_warehouse WHERE stop_id=#{id}")
  void clearStopWarehouses(String id);

  @Insert("INSERT INTO stop_warehouse(stop_id,warehouse_id) VALUES(#{id},#{warehouse})")
  void bindWarehouse(@Param("id") String id, @Param("warehouse") String warehouse);

  @Select("SELECT * FROM vehicle ORDER BY created_at DESC,id DESC")
  List<Vehicle> vehicles();

  @Select("SELECT * FROM vehicle WHERE id=#{id}")
  Vehicle vehicle(String id);

  @Select("SELECT * FROM vehicle WHERE id=#{id} FOR UPDATE")
  Vehicle lockVehicle(String id);

  @Select("SELECT stop_id FROM vehicle_stop WHERE vehicle_id=#{id} ORDER BY stop_id")
  List<String> vehicleStops(String id);

  @Insert(
      "INSERT INTO vehicle(id,name,external_vehicle_name,warehouse_id,enabled)"
          + " VALUES(#{id},#{v.name},#{v.externalVehicleName},#{v.warehouseId},#{v.enabled})")
  void insertVehicle(@Param("id") String id, @Param("v") VehicleInput v);

  @Update(
      "UPDATE vehicle SET"
          + " name=#{v.name},external_vehicle_name=#{v.externalVehicleName},warehouse_id=#{v.warehouseId},enabled=#{v.enabled},version=version+1,updated_at=UTC_TIMESTAMP(3)"
          + " WHERE id=#{id} AND version=#{v.expectedVersion}")
  int updateVehicle(@Param("id") String id, @Param("v") VehicleInput v);

  @Update(
      "UPDATE vehicle SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND"
          + " version=#{version}")
  int bumpVehicle(@Param("id") String id, @Param("version") int version);

  @Delete("DELETE FROM vehicle_stop WHERE vehicle_id=#{id}")
  void clearVehicleStops(String id);

  @Insert("INSERT INTO vehicle_stop(vehicle_id,stop_id) VALUES(#{id},#{stop})")
  void bindStop(@Param("id") String id, @Param("stop") String stop);

  @Select("SELECT * FROM compartment WHERE vehicle_id=#{id} ORDER BY hardware_no")
  List<Compartment> compartments(String id);

  @Select("SELECT * FROM compartment WHERE id=#{id} FOR UPDATE")
  Compartment lockCompartment(String id);

  @Insert(
      "INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled)"
          + " VALUES(#{id},#{vehicle},#{no},#{no},true)")
  void insertCompartment(
      @Param("id") String id, @Param("vehicle") String vehicle, @Param("no") String no);

  @Update(
      "UPDATE compartment SET"
          + " label=#{v.label},enabled=#{v.enabled},version=version+1,updated_at=UTC_TIMESTAMP(3)"
          + " WHERE id=#{id} AND version=#{v.expectedVersion}")
  int updateCompartment(@Param("id") String id, @Param("v") CompartmentInput v);

  @Select("SELECT id,name FROM external_catalog_resource WHERE resource=#{resource} ORDER BY id")
  List<ExternalResource> external(String resource);

  @Select(
      "SELECT hardware_no FROM external_catalog_compartment WHERE vehicle_name=#{name} ORDER BY"
          + " hardware_no")
  List<String> hardware(String name);

  @Select("SELECT warehouse_id,version,config FROM warehouse_rule WHERE warehouse_id=#{id}")
  RuleRow rule(String id);

  @Insert("INSERT INTO warehouse_rule(warehouse_id,config) VALUES(#{id},#{config})")
  void insertRule(@Param("id") String id, @Param("config") String config);

  @Update(
      "UPDATE warehouse_rule SET config=#{config},version=version+1 WHERE warehouse_id=#{id} AND"
          + " version=#{version}")
  int updateRule(
      @Param("id") String id, @Param("config") String config, @Param("version") int version);
}
