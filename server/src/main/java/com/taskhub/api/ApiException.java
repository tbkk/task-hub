package com.taskhub.api;

public class ApiException extends RuntimeException {
    private final int status;
    private final int code;
    public ApiException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public int code() { return code; }
}
