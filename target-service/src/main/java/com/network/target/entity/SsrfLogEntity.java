package com.network.target.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SsrfLogEntity {
    private Integer id;
    private String requestUrl;
    private String requestMethod;
    private Integer responseCode;
    private String responseBody;
    private LocalDateTime requestTime;
    private String sourceIp;
}
