package com.network.target.common;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一接口返回对象（自动处理JSON编码，避免手动拼接乱码）
 */
@Data
public class R {
    // 状态码（200成功，500失败）
    private Integer code;
    // 提示信息
    private String msg;
    // 返回数据
    private Map<String, Object> data = new HashMap<>();

    // 私有构造，禁止外部new
    private R() {}

    // 成功返回
    public static R ok() {
        R r = new R();
        r.setCode(200);
        r.setMsg("操作成功");
        return r;
    }

    // 失败返回
    public static R error() {
        R r = new R();
        r.setCode(500);
        r.setMsg("操作失败");
        return r;
    }

    // 链式设置msg
    public R msg(String msg) {
        this.setMsg(msg);
        return this;
    }

    // 链式设置数据
    public R data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}