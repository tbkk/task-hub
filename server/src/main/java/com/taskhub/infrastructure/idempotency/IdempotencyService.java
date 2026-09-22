package com.taskhub.infrastructure.idempotency;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotencyService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuthorizationService auth;
  private final TransactionTemplate transaction;

  public IdempotencyService(
      JdbcTemplate db,
      ObjectMapper json,
      AuthorizationService auth,
      PlatformTransactionManager manager) {
    this.db = db;
    this.json = json;
    this.auth = auth;
    transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 调用方须在进入本服务前完成操作级鉴权；业务写入放在 supplier 内，与结果原子提交。 */
  public <T> T execute(
      Actor actor,
      String route,
      String key,
      Object body,
      Class<T> resultType,
      Supplier<T> operation) {
    if (route == null
        || route.isBlank()
        || route.length() > 160
        || key == null
        || key.isBlank()
        || key.length() > 128) throw new ApiException(400, 40000, "有效 Idempotency-Key 必填");
    String hash = digest(canonical(json.valueToTree(body)).toString());
    return transaction.execute(
        status -> {
          auth.lockActor(actor);
          var identity = auth.refresh(actor);
          if (actor.workspace() != null) auth.requireWorkspace(actor, actor.workspace());
          String permissionHash =
              digest(
                  canonical(
                          json.valueToTree(
                              Map.of(
                                  "grants",
                                  identity.grants(),
                                  "platformGrants",
                                  identity.platformGrants())))
                      .toString());
          String workspace = actor.workspace() == null ? "ADMIN" : actor.workspace();
          var rows =
              db.queryForList(
                  "SELECT request_hash,authorization_hash,response FROM idempotency_record WHERE"
                      + " actor_id=? AND workspace=? AND route=? AND idempotency_key=?",
                  actor.employeeId(),
                  workspace,
                  route,
                  key);
          if (!rows.isEmpty()) {
            var old = rows.get(0);
            if (!hash.equals(old.get("request_hash")))
              throw new ApiException(409, 40901, "同一请求标识的内容不一致");
            if (!permissionHash.equals(old.get("authorization_hash")))
              throw new ApiException(403, 40302, "授权已变化，请重新查询实际业务结果");
            try {
              return json.readValue((String) old.get("response"), resultType);
            } catch (Exception e) {
              throw new IllegalStateException("幂等结果无法读取", e);
            }
          }
          T result = operation.get();
          try {
            db.update(
                "INSERT INTO"
                    + " idempotency_record(id,actor_id,workspace,route,idempotency_key,request_hash,authorization_hash,response)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                actor.employeeId(),
                workspace,
                route,
                key,
                hash,
                permissionHash,
                json.writeValueAsString(result));
          } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("幂等结果无法保存", e);
          }
          return result;
        });
  }

  private JsonNode canonical(JsonNode value) {
    if (value.isObject()) {
      ObjectNode result = json.createObjectNode();
      var keys = new ArrayList<String>();
      value.fieldNames().forEachRemaining(keys::add);
      Collections.sort(keys);
      keys.forEach(k -> result.set(k, canonical(value.get(k))));
      return result;
    }
    if (value.isArray()) {
      ArrayNode result = json.createArrayNode();
      value.forEach(v -> result.add(canonical(v)));
      return result;
    }
    return value;
  }

  private static String digest(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
