package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.CustomScanRequest;
import com.network.monitor.service.OperLogService;
import com.network.monitor.service.VulnScanService;
import com.network.monitor.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 主动漏洞扫描控制器
 */
@RestController
@RequestMapping("/api/vuln/scan")
public class VulnScanController {

    @Autowired
    private VulnScanService vulnScanService;
    
    @Autowired
    private OperLogService operLogService;

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> startScan(@RequestBody(required = false) Map<String, Object> request,
                                                       HttpServletRequest httpRequest) {
        String scanType = request != null && request.get("scanType") != null
                ? String.valueOf(request.get("scanType")) : "QUICK";
        Map<String, Object> result = vulnScanService.startScan(scanType);
        operLogService.logOperation(SecurityUtil.getCurrentUsername(), "INSERT", "漏洞扫描", 
            "启动扫描：" + scanType, "start", "/api/vuln/scan/start", getClientIp(httpRequest), 0);
        return ApiResponse.success(result);
    }

    /**
     * 启动自定义扫描
     */
    @PostMapping("/start-custom")
    public ApiResponse<Map<String, Object>> startCustomScan(@RequestBody CustomScanRequest request,
                                                             HttpServletRequest httpRequest) {
        List<Long> interfaceIds = request != null ? request.getInterfaceIds() : null;
        Map<String, Object> result = vulnScanService.startCustomScan(interfaceIds);
        operLogService.logOperation(SecurityUtil.getCurrentUsername(), "INSERT", "漏洞扫描", 
            "启动自定义扫描，共" + (interfaceIds != null ? interfaceIds.size() : 0) + "个接口", 
            "startCustom", "/api/vuln/scan/start-custom", getClientIp(httpRequest), 0);
        return ApiResponse.success(result);
    }

    @GetMapping("/progress")
    public ApiResponse<Map<String, Object>> getScanProgress() {
        return ApiResponse.success(vulnScanService.getScanProgress());
    }

    @GetMapping("/result")
    public ApiResponse<Map<String, Object>> getScanResult() {
        return ApiResponse.success(vulnScanService.getScanResult());
    }

    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> syncResult(HttpServletRequest httpRequest) {
        Map<String, Object> result = vulnScanService.syncCurrentResults();
        operLogService.logOperation(SecurityUtil.getCurrentUsername(), "SYNC", "漏洞扫描", 
            "同步扫描结果", "sync", "/api/vuln/scan/sync", getClientIp(httpRequest), 0);
        return ApiResponse.success(result);
    }

    @PostMapping("/control")
    public ApiResponse<Map<String, Object>> controlScan(@RequestBody Map<String, Object> request,
                                                         HttpServletRequest httpRequest) {
        String action = request != null && request.get("action") != null
                ? String.valueOf(request.get("action")) : "";
        Map<String, Object> result = vulnScanService.controlScan(action);
        operLogService.logOperation(SecurityUtil.getCurrentUsername(), "UPDATE", "漏洞扫描", 
            "控制扫描：" + action, "control", "/api/vuln/scan/control", getClientIp(httpRequest), 0);
        return ApiResponse.success(result);
    }

    @GetMapping("/interfaces")
    public ApiResponse<Map<String, Object>> getScanInterfaces(@RequestParam(required = false) String scanType) {
        return ApiResponse.success(vulnScanService.getScanInterfaces(scanType));
    }

    /**
     * 获取可选择的接口列表（用于自定义扫描）
     */
    @GetMapping("/selectable-interfaces")
    public ApiResponse<Map<String, Object>> getSelectableInterfaces(
            @RequestParam(required = false) String vulnType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Long targetId) {
        return ApiResponse.success(vulnScanService.getSelectableInterfaces(vulnType, riskLevel, targetId));
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
