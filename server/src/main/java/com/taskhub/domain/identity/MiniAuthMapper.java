package com.taskhub.domain.identity;

import com.taskhub.domain.identity.MiniAuthModels.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MiniAuthMapper {
  @Select("SELECT employee_id FROM wechat_binding WHERE app_id=#{appId} AND openid=#{openid}")
  String wechatOwner(@Param("appId") String appId, @Param("openid") String openid);

  @Select("SELECT openid FROM wechat_binding WHERE employee_id=#{employeeId} AND app_id=#{appId}")
  String employeeWechat(@Param("employeeId") String employeeId, @Param("appId") String appId);

  @Insert(
      "INSERT INTO wechat_binding(id,employee_id,app_id,openid)"
          + " VALUES(#{id},#{employeeId},#{appId},#{openid})")
  void bind(
      @Param("id") String id,
      @Param("employeeId") String employeeId,
      @Param("appId") String appId,
      @Param("openid") String openid);

  @Insert(
      "INSERT INTO wechat_binding_challenge(token_hash,app_id,openid,expires_at)"
          + " VALUES(#{tokenHash},#{appId},#{openid},#{expiresAt})")
  void insertBinding(Binding binding);

  @Select(
      "SELECT token_hash,app_id,openid,phone,expires_at,consumed_at FROM wechat_binding_challenge"
          + " WHERE token_hash=#{hash} FOR UPDATE")
  Binding lockBinding(String hash);

  @Update("UPDATE wechat_binding_challenge SET phone=#{phone} WHERE token_hash=#{hash}")
  void bindingPhone(@Param("hash") String hash, @Param("phone") String phone);

  @Update(
      "UPDATE wechat_binding_challenge SET consumed_at=UTC_TIMESTAMP(3) WHERE token_hash=#{hash}")
  void consumeBinding(String hash);

  @Insert(
      "INSERT INTO sms_challenge(id,phone,purpose,code_hash,binding_hash,expires_at)"
          + " VALUES(#{id},#{phone},#{purpose},#{codeHash},#{bindingHash},#{expiresAt})")
  void insertChallenge(Challenge challenge);

  @Select(
      "SELECT id,phone,purpose,code_hash,binding_hash,attempts,expires_at,consumed_at FROM"
          + " sms_challenge WHERE id=#{id} FOR UPDATE")
  Challenge lockChallenge(String id);

  @Update("UPDATE sms_challenge SET attempts=attempts+1 WHERE id=#{id}")
  void failed(String id);

  @Update("UPDATE sms_challenge SET consumed_at=UTC_TIMESTAMP(3) WHERE id=#{id}")
  void consumeChallenge(String id);
}
