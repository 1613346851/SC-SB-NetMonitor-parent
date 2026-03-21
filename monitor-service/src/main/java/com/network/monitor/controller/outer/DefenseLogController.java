package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.mapper.DefenseLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/defense")
public class DefenseLogController {

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getDefenseLogs(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String defenseType,
            @RequestParam(required = false) String defenseTarget,
            @RequestParam(required = false) Integer executeStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            int offset = (page - 1) * size;

            LocalDateTime startTime = null;
            LocalDateTime endTime = null;
            if (startDate != null && !startDate.isEmpty()) {
                startTime = LocalDateTime.parse(startDate.replace(" ", "T").substring(0, 19));
            }
            if (endDate != null && !endDate.isEmpty()) {
                endTime = LocalDateTime.parse(endDate.replace(" ", "T").substring(0, 19));
            }

            List<DefenseLogEntity> list = defenseLogMapper.selectByCondition(
                eventId, defenseType, defenseTarget, executeStatus, startTime, endTime, offset, size
            );

            long total = defenseLogMapper.countByCondition(
                eventId, defenseType, defenseTarget, executeStatus, startTime, endTime
            );

            List<Map<String, Object>> resultList = new java.util.ArrayList<>();
            for (DefenseLogEntity entity : list) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", entity.getId());
                item.put("defenseType", entity.getDefenseType());
                item.put("defenseAction", entity.getDefenseAction());
                item.put("defenseTarget", entity.getDefenseTarget());
                item.put("eventId", entity.getEventId());
                item.put("isFirst", entity.getIsFirst());
                item.put("attackId", entity.getAttackId());
                item.put("trafficId", entity.getTrafficId());
                item.put("ruleId", entity.getRuleId());
                item.put("defenseReason", entity.getDefenseReason());
                item.put("expireTime", entity.getExpireTime() != null ? 
                    entity.getExpireTime().format(DATE_FORMATTER) : null);
                item.put("executeStatus", entity.getExecuteStatus());
                item.put("executeResult", entity.getExecuteResult());
                item.put("operator", entity.getOperator());
                item.put("createTime", entity.getExecuteTime() != null ? 
                    entity.getExecuteTime().format(DATE_FORMATTER) : null);
                resultList.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("list", resultList);
            result.put("total", total);
            result.put("pageNum", page);
            result.put("pageSize", size);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询防御日志失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/by-attack/{attackId}")
    public ApiResponse<List<DefenseLogEntity>> getByAttackId(@PathVariable Long attackId) {
        try {
            List<DefenseLogEntity> list = defenseLogMapper.selectByCondition(
                null, null, null, null, null, null, 0, 100
            );
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("根据攻击 ID 查询防御日志失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getDefenseLogById(@PathVariable Long id) {
        try {
            DefenseLogEntity entity = defenseLogMapper.selectById(id);
            if (entity == null) {
                return ApiResponse.error("防御日志不存在");
            }
            
            Map<String, Object> item = new HashMap<>();
            item.put("id", entity.getId());
            item.put("defenseType", entity.getDefenseType());
            item.put("defenseAction", entity.getDefenseAction());
            item.put("defenseTarget", entity.getDefenseTarget());
            item.put("eventId", entity.getEventId());
            item.put("isFirst", entity.getIsFirst());
            item.put("attackId", entity.getAttackId());
            item.put("trafficId", entity.getTrafficId());
            item.put("ruleId", entity.getRuleId());
            item.put("defenseReason", entity.getDefenseReason());
            item.put("expireTime", entity.getExpireTime() != null ? 
                entity.getExpireTime().format(DATE_FORMATTER) : null);
            item.put("executeStatus", entity.getExecuteStatus());
            item.put("executeResult", entity.getExecuteResult());
            item.put("operator", entity.getOperator());
            item.put("createTime", entity.getExecuteTime() != null ? 
                entity.getExecuteTime().format(DATE_FORMATTER) : null);
            
            return ApiResponse.success(item);
        } catch (Exception e) {
            log.error("查询防御日志失败：id={}", id, e);
            return ApiResponse.error("查询失败");
        }
    }
}
