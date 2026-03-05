package com.network.monitor.common.exception;

import lombok.Getter;

/**
 * 参数校验异常类
 */
@Getter
public class ParamException extends BizException {

    public ParamException(String message) {
        super(400, message);
    }

    public ParamException(String paramName, String reason) {
        super(400, "参数 [" + paramName + "] 校验失败：" + reason);
    }
}
