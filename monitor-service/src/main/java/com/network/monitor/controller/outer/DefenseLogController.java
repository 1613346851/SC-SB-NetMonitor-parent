package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.DefenseMonitorEntity;
import com.network.monitor.mapper.DefenseMonitorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 防御日志查询控制器（对外前端业务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/defense")
public class DefenseLogController {

    @Autowired
    private DefenseMonitorMapper defenseMonitorMapper;

    /**
     * 分页查询防御日志
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getDefenseLogs(
            @RequestParam(required = false) String defenseType,
            @RequestParam(required = false) Long attackId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        try {
            int offset = (pageNum - 1) * pageSize;
            
            List<DefenseMonitorEntity> list = defenseMonitorMapper.selectByCondition(
                defenseType, attackId, null, null, offset, pageSize
            );
            
            long total = defenseMonitorMapper.countByCondition(
                defenseType, attackId, null, null
            );

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询防御日志失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 根据攻击 ID 查询关联的防御日志
     */
    @GetMapping("/by-attack/{attackId}")
    public ApiResponse<List<DefenseMonitorEntity>> getByAttackId(@PathVariable Long attackId) {
        try {
            List<DefenseMonitorEntity> list = defenseMonitorMapper.selectByCondition(
                null, attackId, null, null, 0, 100
            );
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("根据攻击 ID 查询防御日志失败：", e);
            return ApiResponse.error("查询失败");
        }
    }
}
