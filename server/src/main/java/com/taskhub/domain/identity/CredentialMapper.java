package com.taskhub.domain.identity;

import com.taskhub.domain.identity.IdentityModels.Credential;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CredentialMapper {
  @Select(
      "SELECT employee_id,username,password_hash,must_change_password FROM admin_credential WHERE"
          + " username=#{username}")
  Credential byUsername(String username);

  @Select(
      "SELECT employee_id,username,password_hash,must_change_password FROM admin_credential WHERE"
          + " employee_id=#{id}")
  Credential byEmployee(String id);

  @Insert(
      "INSERT INTO admin_credential(employee_id,username,password_hash,must_change_password)"
          + " VALUES(#{id},#{username},#{hash},#{temporary})")
  void insert(
      @Param("id") String id,
      @Param("username") String username,
      @Param("hash") String hash,
      @Param("temporary") boolean temporary);

  @Update(
      "UPDATE admin_credential SET"
          + " username=#{username},password_hash=#{hash},must_change_password=#{temporary} WHERE"
          + " employee_id=#{id}")
  void update(
      @Param("id") String id,
      @Param("username") String username,
      @Param("hash") String hash,
      @Param("temporary") boolean temporary);

  @Update(
      "UPDATE admin_credential SET password_hash=#{hash},must_change_password=false WHERE"
          + " employee_id=#{id}")
  void change(@Param("id") String id, @Param("hash") String hash);
}
