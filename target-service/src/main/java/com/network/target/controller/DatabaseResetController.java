package com.network.target.controller;

import com.network.target.common.ApiResponse;
import com.network.target.repository.SerializedObjectRepository;
import com.network.target.repository.SsrfLogRepository;
import com.network.target.repository.XxeLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/target/db")
@Slf4j
public class DatabaseResetController {

    private final SerializedObjectRepository serializedObjectRepository;
    private final SsrfLogRepository ssrfLogRepository;
    private final XxeLogRepository xxeLogRepository;

    public DatabaseResetController(
            SerializedObjectRepository serializedObjectRepository,
            SsrfLogRepository ssrfLogRepository,
            XxeLogRepository xxeLogRepository) {
        this.serializedObjectRepository = serializedObjectRepository;
        this.ssrfLogRepository = ssrfLogRepository;
        this.xxeLogRepository = xxeLogRepository;
    }

    @GetMapping("/status")
    public ApiResponse getDatabaseStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("serialized_objects", serializedObjectRepository.count());
        status.put("ssrf_logs", ssrfLogRepository.count());
        status.put("xxe_logs", xxeLogRepository.count());
        
        long total = (long) status.get("serialized_objects") 
                + (long) status.get("ssrf_logs") 
                + (long) status.get("xxe_logs");
        status.put("total_records", total);

        return ApiResponse.success()
                .message("获取数据库状态成功")
                .data("status", status);
    }

    @DeleteMapping("/reset/{tableName}")
    public ApiResponse resetTable(@PathVariable String tableName) {
        try {
            int deleted = 0;
            String message;
            
            switch (tableName.toLowerCase()) {
                case "sys_serialized_object":
                case "serialized_objects":
                case "deserial":
                    deleted = serializedObjectRepository.deleteAll();
                    message = "已清空序列化对象表";
                    log.info("【数据库重置】清空sys_serialized_object表，删除{}条记录", deleted);
                    break;
                    
                case "sys_ssrf_log":
                case "ssrf_logs":
                case "ssrf":
                    deleted = ssrfLogRepository.deleteAll();
                    message = "已清空SSRF日志表";
                    log.info("【数据库重置】清空sys_ssrf_log表，删除{}条记录", deleted);
                    break;
                    
                case "sys_xxe_log":
                case "xxe_logs":
                case "xxe":
                    deleted = xxeLogRepository.deleteAll();
                    message = "已清空XXE日志表";
                    log.info("【数据库重置】清空sys_xxe_log表，删除{}条记录", deleted);
                    break;
                    
                default:
                    return ApiResponse.error()
                            .message("未知的表名：" + tableName)
                            .data("available_tables", new String[]{
                                "sys_serialized_object (反序列化对象)",
                                "sys_ssrf_log (SSRF日志)",
                                "sys_xxe_log (XXE日志)"
                            });
            }

            return ApiResponse.success()
                    .message(message)
                    .data("deleted_count", deleted)
                    .data("table_name", tableName);

        } catch (Exception e) {
            log.error("重置表失败：{}", tableName, e);
            return ApiResponse.error()
                    .message("重置表失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/reset-all")
    public ApiResponse resetAllTables() {
        try {
            Map<String, Integer> results = new HashMap<>();
            
            int deserialDeleted = serializedObjectRepository.deleteAll();
            results.put("sys_serialized_object", deserialDeleted);
            log.info("【数据库重置】清空sys_serialized_object表，删除{}条记录", deserialDeleted);
            
            int ssrfDeleted = ssrfLogRepository.deleteAll();
            results.put("sys_ssrf_log", ssrfDeleted);
            log.info("【数据库重置】清空sys_ssrf_log表，删除{}条记录", ssrfDeleted);
            
            int xxeDeleted = xxeLogRepository.deleteAll();
            results.put("sys_xxe_log", xxeDeleted);
            log.info("【数据库重置】清空sys_xxe_log表，删除{}条记录", xxeDeleted);

            int totalDeleted = deserialDeleted + ssrfDeleted + xxeDeleted;

            return ApiResponse.success()
                    .message("已清空所有漏洞测试数据表")
                    .data("deleted_records", results)
                    .data("total_deleted", totalDeleted);

        } catch (Exception e) {
            log.error("重置所有表失败", e);
            return ApiResponse.error()
                    .message("重置失败：" + e.getMessage());
        }
    }
}
