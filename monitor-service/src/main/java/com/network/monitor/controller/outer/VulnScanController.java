package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.service.VulnScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
