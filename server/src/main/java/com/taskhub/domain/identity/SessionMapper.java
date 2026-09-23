package com.taskhub.domain.identity;

import com.taskhub.domain.identity.IdentityModels.SessionRow;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SessionMapper {
  @Select(
      "SELECT token_hash,employee_id,channel,expires_at FROM auth_session WHERE token_hash=#{hash}")
  SessionRow find(String hash);

  @Insert(
      "INSERT INTO auth_session(token_hash,employee_id,channel,expires_at)"
          + " VALUES(#{tokenHash},#{employeeId},#{channel},#{expiresAt})")
  void insert(SessionRow session);

  @Delete("DELETE FROM auth_session WHERE token_hash=#{hash}")
  void revoke(String hash);

  @Delete("DELETE FROM auth_session WHERE employee_id=#{id}")
  void revokeAll(String id);

  @Delete("DELETE FROM auth_session WHERE employee_id=#{id} AND channel='ADMIN'")
  void revokeAdmin(String id);
}
