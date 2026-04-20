package com.network.monitor.dto;

import lombok.Data;

@Data
public class WhitelistDTO {

    private Long id;

    private String whitelistType;

    private String whitelistValue;

    private String description;

    private Integer enabled;

    private Integer priority;
}
