package com.taskhub.domain.masterdata;

import com.taskhub.api.*;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MasterdataAccess {
  final AuthorizationService auth;

  public MasterdataAccess(AuthorizationService auth) {
    this.auth = auth;
  }

  public PlatformGrant grant(Actor actor, String capability) {
    return auth.refresh(actor).platformGrants().stream()
        .filter(g -> g.capability().equals(capability))
        .findFirst()
        .orElseThrow(AuthorizationService::denied);
  }

  public boolean covers(PlatformGrant grant, String warehouse) {
    return AuthorizationService.covers(grant.scope(), grant.warehouseIds(), warehouse);
  }

  public void require(Actor actor, String capability, String warehouse) {
    auth.requirePlatform(actor, capability, warehouse);
  }

  public void lock(Actor actor) {
    auth.lockActor(actor);
  }

  public static String text(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim()))
      throw bad("文字不能为空、超长或包含首尾空格");
    return value;
  }

  public static void enabled(Boolean value) {
    if (value == null) throw bad("enabled 必填");
  }

  public static void version(Integer expected, int actual) {
    if (expected == null || expected < 0) throw bad("expectedVersion 必填且非负");
    if (expected != actual) throw new ApiException(409, 40902, "版本已变化，请刷新后重试");
  }

  public static <T> T found(T v) {
    if (v == null) throw new ApiException(404, 404, "对象不存在");
    return v;
  }

  public static ApiException bad(String message) {
    return new ApiException(400, 400, message);
  }

  public static ApiException conflict() {
    return new ApiException(409, 40903, "资源已存在或被关联使用");
  }

  public static List<String> ids(List<String> ids, boolean nonempty) {
    if (ids == null
        || (nonempty && ids.isEmpty())
        || ids.size() > 100
        || new HashSet<>(ids).size() != ids.size()) throw bad("关联列表无效");
    ids.forEach(id -> text(id, 36));
    return ids;
  }

  public static <T> PageResponse<T> page(List<T> items, int page, int size) {
    if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
    long start = (long) (page - 1) * size;
    return new PageResponse<>(
        start >= items.size()
            ? List.of()
            : items.subList((int) start, (int) Math.min(start + size, items.size())),
        items.size(),
        page,
        size);
  }
}
