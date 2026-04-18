package com.reportai.hub.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果封装类
 *
 * @param <T> 返回数据类型
 * @author skill-hub
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Integer SUCCESS_CODE = 200;
    private static final Integer ERROR_CODE = 500;

    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(SUCCESS_CODE);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(SUCCESS_CODE);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(ERROR_CODE, message);
    }

    public boolean isSuccess() {
        return this.code != null && SUCCESS_CODE.equals(this.code);
    }
}
