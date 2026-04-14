package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 输入参数DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputParamDTO {

    /**
     * 参数名
     */
    private String name;

    /**
     * 参数类型
     */
    private String type;

    /**
     * 参数来源
     */
    private String source;

    /**
     * 是否必填
     */
    private Boolean required;

    /**
     * 默认值
     */
    private String defaultValue;
}
