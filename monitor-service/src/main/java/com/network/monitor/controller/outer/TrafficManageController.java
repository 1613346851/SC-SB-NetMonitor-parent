package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.mapper.TrafficMonitorMapper;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
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

    @Autowired
    private AuthService authService;

    @Autowired
    private OperLogService operLogService;

    /**
     * 分页查询流量记录
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getTrafficList(
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String targetIp,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String requestUri,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        try {
            if (pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize < 1 || pageSize > 100) {
                pageSize = 10;
            }
            
            int offset = (pageNum - 1) * pageSize;
            
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            
            String orderBy = buildOrderBy(sortField, sortOrder);
            
            List<TrafficMonitorEntity> list = trafficMonitorMapper.selectByCondition(
                sourceIp, targetIp, httpMethod, requestUri, startDateTime, endDateTime, offset, pageSize, orderBy
            );
            
            long total = trafficMonitorMapper.countByCondition(
                sourceIp, targetIp, httpMethod, requestUri, startDateTime, endDateTime
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
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTraffic(@PathVariable Long id, HttpServletRequest request) {
        try {
            trafficMonitorMapper.deleteById(id);
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "流量管理", 
                "删除流量记录ID：" + id, "delete", "/api/traffic/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除流量记录失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    /**
     * 导出流量数据为CSV
     */
    @GetMapping("/export")
    public void exportTraffic(
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String targetIp,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String requestUri,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        
        try {
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            String orderBy = buildOrderBy(sortField, sortOrder);
            
            List<TrafficMonitorEntity> list = trafficMonitorMapper.selectByCondition(
                sourceIp, targetIp, httpMethod, requestUri, startDateTime, endDateTime, 0, 10000, orderBy
            );
            
            operLogService.logOperation(authService.getCurrentUsername(), "EXPORT", "流量管理", 
                "导出流量数据，共" + list.size() + "条", "export", "/api/traffic/export", getClientIp(httpRequest), 0);
            
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=traffic_export.csv");
            
            PrintWriter writer = response.getWriter();
            writer.write("\uFEFF");
            writer.println("ID,流量ID,请求时间,源IP,目标IP,源端口,目标端口,HTTP方法,请求URI,响应状态,响应时间(ms)");
            
            for (TrafficMonitorEntity item : list) {
                writer.println(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    item.getId(),
                    escapeCsv(item.getTrafficId()),
                    item.getRequestTime(),
                    escapeCsv(item.getSourceIp()),
                    escapeCsv(item.getTargetIp()),
                    item.getSourcePort(),
                    item.getTargetPort(),
                    escapeCsv(item.getHttpMethod()),
                    escapeCsv(item.getRequestUri()),
                    item.getResponseStatus(),
                    item.getResponseTime()
                ));
            }
            
            writer.flush();
        } catch (IOException e) {
            log.error("导出流量数据失败：", e);
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

    /**
     * 构建排序语句
     */
    private String buildOrderBy(String sortField, String sortOrder) {
        String field = mapSortField(sortField);
        String order = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return field + " " + order;
    }

    /**
     * 映射排序字段（防止SQL注入）
     */
    private String mapSortField(String sortField) {
        switch (sortField) {
            case "id": return "id";
            case "requestTime": return "request_time";
            case "sourceIp": return "source_ip";
            case "targetIp": return "target_ip";
            case "responseStatus": return "response_status";
            case "responseTime": return "response_time";
            default: return "id";
        }
    }

    /**
     * CSV字段转义
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
