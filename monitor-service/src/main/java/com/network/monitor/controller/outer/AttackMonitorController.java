package com.network.monitor.controller.outer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.monitor.cache.SysConfigCache;
import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 攻击监测控制器（对外前端业务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/attack")
public class AttackMonitorController {

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysConfigCache sysConfigCache;

    /**
     * 分页查询攻击记录
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getAttackList(
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) Integer handled,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        try {
            int offset = (pageNum - 1) * pageSize;
            
            List<AttackMonitorEntity> list = attackMonitorMapper.selectByCondition(
                attackType, riskLevel, sourceIp, handled, null, null, offset, pageSize
            );
            
            long total = attackMonitorMapper.countByCondition(
                attackType, riskLevel, sourceIp, handled, null, null
            );

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询攻击记录失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 查询未处理的高危告警
     */
    @GetMapping("/unhandled/high-risk")
    public ApiResponse<List<AttackMonitorEntity>> getUnhandledHighRisk() {
        try {
            List<AttackMonitorEntity> list = attackMonitorMapper.selectUnHandledHighRisk();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询未处理高危告警失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    /**
     * 处理攻击记录
     */
    @PostMapping("/handle/{id}")
    public ApiResponse<Void> handleAttack(
            @PathVariable Long id,
            @RequestParam String remark) {
        try {
            attackMonitorMapper.updateHandled(id, 1, remark);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("处理攻击记录失败：", e);
            return ApiResponse.error("处理失败");
        }
    }

    /**
     * 准实时告警接口（SSE）
     * 使用长轮询方式推送未处理的高危告警
     */
    @GetMapping("/alert/realtime")
    public void getRealTimeAlerts(
            @RequestParam(required = false, defaultValue = "30000") Long timeout,
            HttpServletResponse response) {
        
        try {
            // 检查告警是否启用
            boolean alertEnabled = sysConfigCache.getBooleanValue("alert.enabled", true);
            if (!alertEnabled) {
                response.getWriter().write("data: []\n\n");
                response.getWriter().write(": alert disabled\n\n");
                response.getWriter().flush();
                return;
            }

            // 从配置缓存获取告警推送间隔
            long pushInterval = sysConfigCache.getLongValue("alert.push.interval", 5000);
            long heartbeatInterval = sysConfigCache.getLongValue("alert.heartbeat.interval", 10000);

            // 设置 SSE 响应头
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < timeout) {
                List<AttackMonitorEntity> alerts = attackMonitorMapper.selectUnHandledHighRisk();
                
                if (alerts != null && !alerts.isEmpty()) {
                    // 发送告警数据
                    response.getWriter().write("data: " + toJson(alerts) + "\n\n");
                    response.getWriter().flush();
                    
                    // 发送后休眠，避免频繁推送
                    Thread.sleep(pushInterval);
                } else {
                    // 发送心跳
                    response.getWriter().write(": heartbeat\n\n");
                    response.getWriter().flush();
                    Thread.sleep(heartbeatInterval);
                }
                
                // 检查客户端是否已断开连接
                if (response.isCommitted()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("准实时告警推送失败：", e);
        }
    }

    /**
     * 将对象转换为 JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败：", e);
            return "{}";
        }
    }
}
