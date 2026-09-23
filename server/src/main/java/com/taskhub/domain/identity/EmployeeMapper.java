package com.taskhub.domain.identity;

import com.taskhub.domain.identity.IdentityModels.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface EmployeeMapper {
  @Select("SELECT home_warehouse_id FROM employee WHERE id=#{id}")
  String homeWarehouse(String id);

  @Select("SELECT enabled FROM warehouse WHERE id=#{id} FOR UPDATE")
  Boolean lockWarehouse(String id);

  @Update("UPDATE employee SET home_warehouse_id=#{warehouseId} WHERE id=#{id}")
  void setHomeWarehouse(@Param("id") String id, @Param("warehouseId") String warehouseId);

  @Select("SELECT EXISTS(SELECT 1 FROM wechat_binding WHERE employee_id=#{id})")
  boolean wechatBound(String id);

  @Select("SELECT id,name,enabled FROM warehouse ORDER BY name,id LIMIT #{limit} OFFSET #{offset}")
  List<WarehouseOption> warehouseOptions(@Param("limit") int limit, @Param("offset") int offset);

  @Select("SELECT COUNT(*) FROM warehouse")
  int warehouseCount();

  @Select("SELECT id,name,phone,enabled,version,created_at,updated_at FROM employee WHERE id=#{id}")
  Employee find(String id);

  @Select(
      "SELECT id,name,phone,enabled,version,created_at,updated_at FROM employee WHERE id=#{id} FOR"
          + " UPDATE")
  Employee lock(String id);

  @Select(
      "SELECT id,name,phone,enabled,version,created_at,updated_at FROM employee WHERE"
          + " phone=#{phone}")
  Employee findByPhone(String phone);

  @Select("SELECT phone FROM verified_phone WHERE employee_id=#{id}")
  String verifiedPhone(String id);

  @Select("SELECT employee_id FROM verified_phone WHERE phone=#{phone}")
  String verifiedOwner(String phone);

  @Insert("INSERT INTO verified_phone(employee_id,phone) VALUES(#{id},#{phone})")
  void verifyPhone(@Param("id") String id, @Param("phone") String phone);

  @Select("SELECT id,role,scope FROM role_grant WHERE employee_id=#{id} ORDER BY role")
  List<GrantRow> roles(String id);

  @Select("SELECT warehouse_id FROM grant_warehouse WHERE grant_id=#{id} ORDER BY warehouse_id")
  List<String> roleWarehouses(String id);

  @Select(
      "SELECT id,capability,scope FROM platform_grant WHERE employee_id=#{id} ORDER BY capability")
  List<PlatformGrantRow> platforms(String id);

  @Select(
      "SELECT warehouse_id FROM platform_grant_warehouse WHERE grant_id=#{id} ORDER BY"
          + " warehouse_id")
  List<String> platformWarehouses(String id);

  @Insert("INSERT INTO employee(id,name,phone,enabled) VALUES(#{id},#{name},#{phone},#{enabled})")
  void insert(
      @Param("id") String id,
      @Param("name") String name,
      @Param("phone") String phone,
      @Param("enabled") boolean enabled);

  @Update(
      "UPDATE employee SET"
          + " name=#{input.name},phone=#{input.phone},enabled=#{input.enabled},version=version+1,updated_at=UTC_TIMESTAMP(3)"
          + " WHERE id=#{id} AND version=#{input.expectedVersion}")
  int update(@Param("id") String id, @Param("input") EmployeeInput input);

  String FILTER =
      "<where><if test='keyword != null'> AND (LOCATE(#{keyword},e.name)>0 OR"
          + " LOCATE(#{keyword},e.phone)>0)</if><if test='enabled != null'> AND"
          + " e.enabled=#{enabled}</if><if test='warehouseId != null'> AND"
          + " e.home_warehouse_id=#{warehouseId}</if><if test='role != null'> AND EXISTS(SELECT 1"
          + " FROM role_grant g WHERE g.employee_id=e.id AND g.role=#{role})</if><if"
          + " test='phoneVerified != null'> AND EXISTS(SELECT 1 FROM verified_phone p WHERE"
          + " p.employee_id=e.id)=#{phoneVerified}</if><if test='wechatBound != null'> AND"
          + " EXISTS(SELECT 1 FROM wechat_binding w WHERE w.employee_id=e.id)=#{wechatBound}</if>"
          + "</where>";

  @Select(
      "<script>SELECT e.id,e.name,e.phone,e.enabled,e.version,e.created_at,e.updated_at FROM"
          + " employee e "
          + FILTER
          + " ORDER BY e.created_at DESC,e.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
  List<Employee> list(
      @Param("keyword") String keyword,
      @Param("enabled") Boolean enabled,
      @Param("warehouseId") String warehouseId,
      @Param("role") String role,
      @Param("phoneVerified") Boolean phoneVerified,
      @Param("wechatBound") Boolean wechatBound,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Select("<script>SELECT COUNT(*) FROM employee e " + FILTER + "</script>")
  int count(
      @Param("keyword") String keyword,
      @Param("enabled") Boolean enabled,
      @Param("warehouseId") String warehouseId,
      @Param("role") String role,
      @Param("phoneVerified") Boolean phoneVerified,
      @Param("wechatBound") Boolean wechatBound);

  @Insert(
      "INSERT INTO role_grant(id,employee_id,role,scope)"
          + " VALUES(#{id},#{employeeId},#{role},#{scope})")
  void addRole(
      @Param("id") String id,
      @Param("employeeId") String employeeId,
      @Param("role") String role,
      @Param("scope") String scope);

  @Insert("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(#{id},#{warehouse})")
  void addRoleWarehouse(@Param("id") String id, @Param("warehouse") String warehouse);

  @Insert(
      "INSERT INTO platform_grant(id,employee_id,capability,scope)"
          + " VALUES(#{id},#{employeeId},#{capability},#{scope})")
  void addPlatform(
      @Param("id") String id,
      @Param("employeeId") String employeeId,
      @Param("capability") String capability,
      @Param("scope") String scope);

  @Insert("INSERT INTO platform_grant_warehouse(grant_id,warehouse_id) VALUES(#{id},#{warehouse})")
  void addPlatformWarehouse(@Param("id") String id, @Param("warehouse") String warehouse);

  @Delete(
      "DELETE FROM grant_warehouse WHERE grant_id IN(SELECT id FROM role_grant WHERE"
          + " employee_id=#{id})")
  void deleteRoleWarehouses(String id);

  @Delete("DELETE FROM role_grant WHERE employee_id=#{id}")
  void deleteRoles(String id);

  @Delete(
      "DELETE FROM platform_grant_warehouse WHERE grant_id IN(SELECT id FROM platform_grant WHERE"
          + " employee_id=#{id})")
  void deletePlatformWarehouses(String id);

  @Delete("DELETE FROM platform_grant WHERE employee_id=#{id}")
  void deletePlatforms(String id);

  @Select("SELECT initialized FROM identity_lock WHERE id=1 FOR UPDATE")
  boolean lockAdministration();

  @Update("UPDATE identity_lock SET initialized=true WHERE id=1")
  void initialized();

  @Select(
      "SELECT COUNT(*) FROM employee e JOIN platform_grant g ON g.employee_id=e.id JOIN"
          + " admin_credential c ON c.employee_id=e.id WHERE e.enabled=true AND"
          + " g.capability='EMPLOYEE_MANAGE' AND g.scope='ALL'")
  int usableAdministrators();

  @Select("SELECT COUNT(*) FROM platform_grant WHERE capability='EMPLOYEE_MANAGE' AND scope='ALL'")
  int administrators();
}
