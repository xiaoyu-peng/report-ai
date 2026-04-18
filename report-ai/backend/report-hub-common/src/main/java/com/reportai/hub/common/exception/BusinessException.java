package com.reportai.hub.common.exception;

import lombok.Getter;

/**
 * 业务异常类
 * 用于封装业务逻辑中产生的异常信息
 *
 * @author skill-hub
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static BusinessException of(String message) {
        return new BusinessException(message);
    }

    public static BusinessException of(Integer code, String message) {
        return new BusinessException(code, message);
    }

    public static BusinessException of(Integer code, String message, Throwable cause) {
        return new BusinessException(code, message, cause);
    }
}
