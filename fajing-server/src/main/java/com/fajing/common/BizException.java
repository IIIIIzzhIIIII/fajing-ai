package com.fajing.common;

/**
 * 业务异常：由全局异常处理器转换为 Result.fail
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
