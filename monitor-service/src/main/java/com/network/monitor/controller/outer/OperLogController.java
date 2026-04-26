package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.util.CsvFileUtil;
import com.network.monitor.entity.OperLogEntity;
import com.network.monitor.service.OperLogService;
import com.network.monitor.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/system/log")
public class OperLogController {

    private static final Logger log = LoggerFactory.getLogger(OperLogController.class);
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private static final Map<String, String> OPER_TYPE_MAP = new LinkedHashMap<>();
    
    static {
        OPER_TYPE_MAP.put("LOGIN", "登录");
        OPER_TYPE_MAP.put("LOGOUT", "登出");
        OPER_TYPE_MAP.put("INSERT", "新增");
        OPER_TYPE_MAP.put("UPDATE", "修改");
        OPER_TYPE_MAP.put("DELETE", "删除");
        OPER_TYPE_MAP.put("EXPORT", "导出");
    }

    @Autowired
    private OperLogService operLogService;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operType,
            @RequestParam(required = false) Integer operStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "operTime") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        Map<String, Object> result = operLogService.listLogsWithPaging(
            username, operType, operStatus, startTime, endTime, pageNum, pageSize, sortField, sortOrder
        );
        return ApiResponse.success(result);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<OperLogEntity> getById(@PathVariable Long id) {
        OperLogEntity log = operLogService.getById(id);
        if (log == null) {
            return ApiResponse.notFound("日志不存在");
        }
        return ApiResponse.success(log);
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        boolean success = operLogService.deleteLog(id);
        if (success) {
            return ApiResponse.success();
        }
        return ApiResponse.error("删除日志失败");
    }
    
    @DeleteMapping("/clear")
    public ApiResponse<Integer> clear(@RequestParam String beforeTime) {
        int count = operLogService.clearLogs(beforeTime);
        return ApiResponse.success(count);
    }
    
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operType,
            @RequestParam(required = false) Integer operStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            List<OperLogEntity> logs = operLogService.listAllLogsForExport(username, operType, operStatus, startTime, endTime);
            
            List<String> headers = Arrays.asList(
                "日志ID", "操作账号", "操作类型", "操作模块", "操作内容",
                "请求方法", "请求URL", "操作IP", "操作地点", "操作状态", "错误信息", "操作时间", "耗时(ms)"
            );
            
            List<Map<String, Object>> csvData = new ArrayList<>();
            for (OperLogEntity operLog : logs) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("日志ID", operLog.getId());
                row.put("操作账号", operLog.getUsername() != null ? operLog.getUsername() : "");
                row.put("操作类型", OPER_TYPE_MAP.getOrDefault(operLog.getOperType(), operLog.getOperType() != null ? operLog.getOperType() : ""));
                row.put("操作模块", operLog.getOperModule() != null ? operLog.getOperModule() : "");
                row.put("操作内容", operLog.getOperContent() != null ? operLog.getOperContent() : "");
                row.put("请求方法", operLog.getOperMethod() != null ? operLog.getOperMethod() : "");
                row.put("请求URL", operLog.getOperUrl() != null ? operLog.getOperUrl() : "");
                row.put("操作IP", operLog.getOperIp() != null ? operLog.getOperIp() : "");
                row.put("操作地点", operLog.getOperLocation() != null ? operLog.getOperLocation() : "");
                row.put("操作状态", operLog.getOperStatus() != null && operLog.getOperStatus() == 0 ? "成功" : "失败");
                row.put("错误信息", operLog.getErrorMsg() != null ? operLog.getErrorMsg() : "");
                row.put("操作时间", operLog.getOperTime() != null ? operLog.getOperTime().format(TIME_FORMATTER) : "");
                row.put("耗时(ms)", operLog.getCostTime() != null ? operLog.getCostTime().toString() : "");
                csvData.add(row);
            }
            
            String fileName = CsvFileUtil.generateCsvFileName("oper_log");
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            response.setCharacterEncoding("UTF-8");
            
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
                CsvFileUtil.writeCsv(writer, headers, csvData, true);
            }
            
            operLogService.logOperation(SecurityUtil.getCurrentUsername(), "EXPORT", "操作日志",
                "导出操作日志数据，共" + logs.size() + "条", "export", "/api/system/log/export",
                getClientIp(request), 0);
            
            log.info("导出操作日志 CSV 成功，共{}条", logs.size());
        } catch (Exception e) {
            log.error("导出操作日志 CSV 失败：", e);
        }
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
