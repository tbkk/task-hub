package com.taskhub.domain.identity;

import com.taskhub.api.ApiException;

public final class IdentityValidation {
  private IdentityValidation() {}

  public static String required(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max) throw bad(field + "格式不正确");
    return value.trim();
  }

  public static String phone(String value) {
    String normalized = required(value, 20, "手机号").replaceFirst("^\\+86", "");
    if (!normalized.matches("1[3-9][0-9]{9}")) throw bad("手机号格式不正确");
    return normalized;
  }

  public static String username(String value) {
    String v = required(value, 80, "用户名");
    if (!v.matches("[a-zA-Z0-9_.@-]{3,80}")) throw bad("用户名格式不正确");
    return v.toLowerCase(java.util.Locale.ROOT);
  }

  public static void password(String value) {
    if (value == null
        || value.length() < 12
        || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || value.isBlank()) throw bad("密码需为12至72字节");
  }

  public static ApiException bad(String message) {
    return new ApiException(400, 40000, message);
  }
}
