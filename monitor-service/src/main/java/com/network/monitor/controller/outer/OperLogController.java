package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.OperLogEntity;
import com.network.monitor.service.OperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/log")
public class OperLogController {

    @Autowired
    private OperLogService operLogService;

    @GetMapping("/list")
    public ApiResponse<List<OperLogEntity>> list(@RequestParam(required = false) String username,
                                                  @RequestParam(required = false) String operType,
                                                  @RequestParam(required = false) Integer operStatus,
                                                  @RequestParam(required = false) String startTime,
                                                  @RequestParam(required = false) String endTime) {
        List<OperLogEntity> logs = operLogService.listLogs(username, operType, operStatus, startTime, endTime);
        return ApiResponse.success(logs);
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
}
