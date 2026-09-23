package com.taskhub.domain.integration;

public interface SmsProvider {
  void send(String phone, String code);

  default String challengeCode() {
    return com.taskhub.domain.identity.TokenCodec.smsCode();
  }
}
