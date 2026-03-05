package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.mapper.TrafficMonitorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流量数据管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/traffic")
public class TrafficManageController {

    @Autowired
    private TrafficMonitorMapper trafficMonitorMapper;

    /**
     * 分页查询流量记录
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getTrafficList(
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String requestUri,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        try {
            int offset = (pageNum - 1) * pageSize;
            
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            
            List<TrafficMonitorEntity> list = trafficMonitorMapper.selectByCondition(
                sourceIp, requestUri, startDateTime, endDateTime, offset, pageSize
            );
            
            long total = trafficMonitorMapper.countByCondition(
                sourceIp, requestUri, startDateTime, endDateTime
            );

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询流量记录失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 根据 ID 查询流量详情
     */
    @GetMapping("/{id}")
    public ApiResponse<TrafficMonitorEntity> getTrafficDetail(@PathVariable Long id) {
        try {
            TrafficMonitorEntity entity = trafficMonitorMapper.selectById(id);
            if (entity == null) {
                return ApiResponse.notFound("流量记录不存在");
            }
            return ApiResponse.success(entity);
        } catch (Exception e) {
            log.error("查询流量详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 删除流量记录
     */
    @PostMapping("/delete/{id}")
    public ApiResponse<Void> deleteTraffic(@PathVariable Long id) {
        try {
            trafficMonitorMapper.deleteById(id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除流量记录失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }
}
