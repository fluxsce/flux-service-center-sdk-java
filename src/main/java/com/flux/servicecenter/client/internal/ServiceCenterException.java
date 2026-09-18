package com.flux.servicecenter.client.internal;

/**
 * 服务端返回的业务错误，code 与网关 v3 哨兵一致，便于 SDK 判断。
 */
public final class ServiceCenterException extends RuntimeException {
    private final String code;

    public ServiceCenterException(String code, String message) {
        super(message == null || message.isEmpty() ? code : message);
        this.code = code == null ? "ERROR" : code;
    }

    public String getCode() {
        return code;
    }
}
