package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.AttackChainDTO;
import com.network.monitor.dto.GeoIpDTO;
import com.network.monitor.dto.IpProfileDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.mapper.TrafficMonitorMapper;
import com.network.monitor.service.GeoIpService;
import com.network.monitor.service.IpProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/trace")
public class AttackTraceController {

    @Autowired
    private IpProfileService ipProfileService;

    @Autowired
    private GeoIpService geoIpService;

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private TrafficMonitorMapper trafficMonitorMapper;

    @GetMapping("/ip/{ip}")
    public ApiResponse<IpProfileDTO> getIpProfile(@PathVariable String ip) {
        try {
            IpProfileDTO profile = ipProfileService.getIpProfile(ip);
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("获取IP画像失败: ip={}", ip, e);
            return ApiResponse.error("获取IP画像失败");
        }
    }

    @GetMapping("/geo/{ip}")
    public ApiResponse<GeoIpDTO> getGeoInfo(@PathVariable String ip) {
        try {
            GeoIpDTO geoInfo = geoIpService.lookup(ip);
            return ApiResponse.success(geoInfo);
        } catch (Exception e) {
            log.error("获取IP地理位置失败: ip={}", ip, e);
            return ApiResponse.error("获取IP地理位置失败");
        }
    }

    @GetMapping("/chain/{ip}")
    public ApiResponse<AttackChainDTO> getAttackChain(
            @PathVariable String ip,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "24") Integer hours) {
        try {
            AttackChainDTO chain;
            if (startTime != null && endTime != null) {
                chain = ipProfileService.getAttackChain(ip, startTime, endTime);
            } else {
                chain = ipProfileService.getRecentAttackChain(ip, hours);
            }
            return ApiResponse.success(chain);
        } catch (Exception e) {
            log.error("获取攻击链失败: ip={}", ip, e);
            return ApiResponse.error("获取攻击链失败");
        }
    }

    @GetMapping("/attacks/{ip}")
    public ApiResponse<Map<String, Object>> getAttackRecords(
            @PathVariable String ip,
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            int offset = (pageNum - 1) * pageSize;
            List<AttackMonitorEntity> list = attackMonitorMapper.selectByCondition(
                    null, attackType, riskLevel, ip, null, startTime, endTime,
                    offset, pageSize, "create_time DESC"
            );
            long total = attackMonitorMapper.countByCondition(
                    null, attackType, riskLevel, ip, null, startTime, endTime
            );

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", total);
            data.put("pageNum", pageNum);
            data.put("pageSize", pageSize);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取攻击记录失败: ip={}", ip, e);
            return ApiResponse.error("获取攻击记录失败");
        }
    }

    @GetMapping("/defenses/{ip}")
    public ApiResponse<Map<String, Object>> getDefenseRecords(
            @PathVariable String ip,
            @RequestParam(required = false) String defenseType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            int offset = (pageNum - 1) * pageSize;
            List<DefenseLogEntity> list = defenseLogMapper.selectByCondition(
                    null, defenseType, ip, null, startTime, endTime,
                    offset, pageSize, "create_time", "DESC"
            );
            long total = defenseLogMapper.countByCondition(
                    null, defenseType, ip, null, startTime, endTime
            );

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", total);
            data.put("pageNum", pageNum);
            data.put("pageSize", pageSize);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取防御记录失败: ip={}", ip, e);
            return ApiResponse.error("获取防御记录失败");
        }
    }

    @GetMapping("/traffic/{ip}")
    public ApiResponse<Map<String, Object>> getTrafficRecords(
            @PathVariable String ip,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String requestUri,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            int offset = (pageNum - 1) * pageSize;
            List<TrafficMonitorEntity> list = trafficMonitorMapper.selectByCondition(
                    ip, null, httpMethod, requestUri, startTime, endTime,
                    offset, pageSize, "create_time DESC"
            );
            long total = trafficMonitorMapper.countByCondition(
                    ip, null, httpMethod, requestUri, startTime, endTime
            );

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", total);
            data.put("pageNum", pageNum);
            data.put("pageSize", pageSize);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取流量记录失败: ip={}", ip, e);
            return ApiResponse.error("获取流量记录失败");
        }
    }

    @GetMapping("/risk-score/{ip}")
    public ApiResponse<Map<String, Object>> getRiskScore(@PathVariable String ip) {
        try {
            Integer score = ipProfileService.calculateRiskScore(ip);
            String level = ipProfileService.getRiskLevel(score);

            Map<String, Object> data = new HashMap<>();
            data.put("ip", ip);
            data.put("score", score);
            data.put("level", level);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取风险评分失败: ip={}", ip, e);
            return ApiResponse.error("获取风险评分失败");
        }
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            int offset = (pageNum - 1) * pageSize;
            List<AttackMonitorEntity> list = attackMonitorMapper.selectByCondition(
                    null, attackType, riskLevel, ip, null, startTime, endTime,
                    offset, pageSize, "create_time DESC"
            );
            long total = attackMonitorMapper.countByCondition(
                    null, attackType, riskLevel, ip, null, startTime, endTime
            );

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", total);
            data.put("pageNum", pageNum);
            data.put("pageSize", pageSize);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("溯源查询失败", e);
            return ApiResponse.error("溯源查询失败");
        }
    }
}
