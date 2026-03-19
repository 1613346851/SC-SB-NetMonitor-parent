package com.network.target.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SerializedObjectEntity {
    private Integer id;
    private String objectName;
    private String objectType;
    private byte[] serializedData;
    private LocalDateTime createTime;
}
