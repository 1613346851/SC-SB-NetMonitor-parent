package com.network.target.common;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一接口返回对象（自动处理JSON编码，避免手动拼接乱码）
 */
@Data
public class ApiResponse {
    // 状态码（200成功，500失败）
    private Integer code;
    // 提示信息
    private String message;
    // 返回数据
    private Map<String, Object> data = new HashMap<>();

    // 私有构造，禁止外部new
    private ApiResponse() {}

    // 成功返回
    public static ApiResponse success() {
        ApiResponse r = new ApiResponse();
        r.setCode(200);
        r.setMessage("操作成功");
        return r;
    }

    // 失败返回
    public static ApiResponse error() {
        ApiResponse r = new ApiResponse();
        r.setCode(500);
        r.setMessage("操作失败");
        return r;
    }

    // 链式设置message
    public ApiResponse message(String message) {
        this.setMessage(message);
        return this;
    }

    // 链式设置数据
    public ApiResponse data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}