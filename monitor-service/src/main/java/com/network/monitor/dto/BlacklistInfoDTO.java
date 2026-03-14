package com.network.monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class BlacklistInfoDTO {
    private Long id;
    private String ip;
    private String reason;
    private String expireTime;
    private String createTime;
    private String operator;
    private Integer status;
    
    /**
     * 封禁历史记录列表（该IP的所有封禁记录）
     */
    private List<BlacklistHistoryDTO> history;
    
    /**
     * 剩余时间（秒）
     */
    private Long remainingSeconds;
    
    private String remainingTime;

    private Integer totalBanCount;
    
    /**
     * 封禁历史记录DTO
     */
    @Data
    public static class BlacklistHistoryDTO {
        private Long id;
        private String reason;
        private String expireTime;
        private String createTime;
        private String operator;
        private Integer status;
    }
}
