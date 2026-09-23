package com.taskhub.domain.integration;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.net.URI;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationSettingsService {
  public record Settings(String provider,String baseUrl,String orgId,String appId,boolean enabled,int version) {}
  public record Input(String provider,String baseUrl,String orgId,String appId,Boolean enabled,Integer expectedVersion,String clientSecret,String accessToken) {}
  private final JdbcTemplate db; private final AuthorizationService auth; private final Environment env;
  public IntegrationSettingsService(JdbcTemplate db,AuthorizationService auth,Environment env){this.db=db;this.auth=auth;this.env=env;}
  public Settings get(Actor actor){auth.requirePlatform(actor,"INTEGRATION_MANAGE",null); var rows=db.query("SELECT provider,base_url,org_id,app_id,enabled,version FROM integration_setting WHERE id=1",(rs,n)->new Settings(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getInt(6))); return rows.isEmpty()?new Settings("","","","",false,0):rows.get(0);}
  @Transactional
  public Settings save(Actor actor,Input input){auth.requirePlatform(actor,"INTEGRATION_MANAGE",null); if(input==null||blank(input.provider())||blank(input.baseUrl())||blank(input.orgId())||blank(input.appId())||input.enabled()==null)throw new ApiException(400,40001,"接入参数不完整"); validate(input.provider(),input.baseUrl());
    var locked=db.query("SELECT provider,base_url,org_id,app_id,enabled,version FROM integration_setting WHERE id=1 FOR UPDATE",(rs,n)->new Settings(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getInt(6))); Settings old=locked.isEmpty()?new Settings("","","","",false,0):locked.get(0); if(input.expectedVersion()!=null&&input.expectedVersion()!=old.version())throw new ApiException(409,40902,"接入配置版本已变化，请刷新后重试");
    String secret=input.clientSecret(),token=input.accessToken(); if(secret==null)secret=db.query("SELECT client_secret FROM integration_setting WHERE id=1",rs->{return rs.next()?rs.getString(1):null;}); if(token==null)token=db.query("SELECT access_token FROM integration_setting WHERE id=1",rs->{return rs.next()?rs.getString(1):null;});
    db.update("INSERT INTO integration_setting(id,provider,base_url,org_id,app_id,client_secret,access_token,enabled,version,updated_by) VALUES(1,?,?,?,?,?,?,?,0,?) ON DUPLICATE KEY UPDATE provider=VALUES(provider),base_url=VALUES(base_url),org_id=VALUES(org_id),app_id=VALUES(app_id),client_secret=VALUES(client_secret),access_token=VALUES(access_token),enabled=VALUES(enabled),version=version+1,updated_by=VALUES(updated_by),updated_at=CURRENT_TIMESTAMP(3)",input.provider(),input.baseUrl(),input.orgId(),input.appId(),secret,token,input.enabled(),actor.employeeId());
    return get(actor);
  }
  private void validate(String provider,String base){ boolean prod=Arrays.stream(env.getActiveProfiles()).anyMatch(p->p.equals("prod")||p.equals("production")); if(prod&&"SIMULATOR".equalsIgnoreCase(provider))throw new ApiException(400,40003,"生产环境禁止模拟提供者"); URI uri;try{uri=URI.create(base);}catch(Exception e){throw new ApiException(400,40001,"基础地址格式不正确");} if(uri.getHost()==null||!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))||(prod&&(!"https".equalsIgnoreCase(uri.getScheme())||!Set.of("gateway.zelostech.com.cn","auth.zelostech.com.cn").contains(uri.getHost()))))throw new ApiException(400,40003,"基础地址不符合当前环境要求"); }
  private static boolean blank(String v){return v==null||v.isBlank()||!v.equals(v.trim());}
}
