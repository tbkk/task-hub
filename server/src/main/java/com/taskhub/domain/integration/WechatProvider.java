package com.taskhub.domain.integration;

public interface WechatProvider {
  record WechatIdentity(String appId, String openid) {}

  WechatIdentity exchange(String code);
}
