package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.CustomScanRequest;
import com.network.monitor.service.VulnScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> startScan(@RequestBody(required = false) Map<String, Object> request) {
        String scanType = request != null && request.get("scanType") != null
                ? String.valueOf(request.get("scanType")) : "QUICK";
        return ApiResponse.success(vulnScanService.startScan(scanType));
    }

    /**
     * 启动自定义扫描
     */
    @PostMapping("/start-custom")
    public ApiResponse<Map<String, Object>> startCustomScan(@RequestBody CustomScanRequest request) {
        List<Long> interfaceIds = request != null ? request.getInterfaceIds() : null;
        return ApiResponse.success(vulnScanService.startCustomScan(interfaceIds));
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
    public ApiResponse<Map<String, Object>> syncResult() {
        return ApiResponse.success(vulnScanService.syncCurrentResults());
    }

    @PostMapping("/control")
    public ApiResponse<Map<String, Object>> controlScan(@RequestBody Map<String, Object> request) {
        String action = request != null && request.get("action") != null
                ? String.valueOf(request.get("action")) : "";
        return ApiResponse.success(vulnScanService.controlScan(action));
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
}
