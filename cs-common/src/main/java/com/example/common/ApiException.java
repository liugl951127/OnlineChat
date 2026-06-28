package com.example.common;

public class ApiException extends RuntimeException {
    private final int code;
    private Object data;
    public ApiException(int code, String msg) { super(msg); this.code = code; }
    public int getCode() { return code; }
    public Object getData() { return data; }
    public ApiException withData(Object data) { this.data = data; return this; }
}