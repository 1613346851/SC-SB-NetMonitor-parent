package com.network.target.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class XxeLogEntity {
    private Integer id;
    private String xmlContent;
    private String parseResult;
    private Boolean hasExternalEntity;
    private LocalDateTime createTime;
}
