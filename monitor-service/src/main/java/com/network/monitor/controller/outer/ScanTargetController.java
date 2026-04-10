package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.ScanTargetEntity;
import com.network.monitor.service.ScanTargetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/scan-target")
public class ScanTargetController {

    @Autowired
    private ScanTargetService scanTargetService;

    @GetMapping("/{id}")
    public ApiResponse<ScanTargetEntity> getById(@PathVariable Long id) {
        try {
            ScanTargetEntity entity = scanTargetService.getById(id);
            if (entity == null) {
                return ApiResponse.notFound("扫描目标不存在");
            }
            return ApiResponse.success(entity);
        } catch (Exception e) {
            log.error("查询扫描目标详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getList(
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            if (page < 1) page = 1;
            if (size < 1 || size > 100) size = 10;

            Map<String, Object> result = scanTargetService.getPage(targetName, targetType, enabled, page, size);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询扫描目标列表失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/enabled")
    public ApiResponse<List<ScanTargetEntity>> getEnabled() {
        try {
            List<ScanTargetEntity> list = scanTargetService.getAllEnabled();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询启用扫描目标失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/all")
    public ApiResponse<List<ScanTargetEntity>> getAll() {
        try {
            List<ScanTargetEntity> list = scanTargetService.getAll();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询所有扫描目标失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @PostMapping("/add")
    public ApiResponse<ScanTargetEntity> add(@RequestBody ScanTargetEntity entity) {
        try {
            ScanTargetEntity created = scanTargetService.create(entity);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建扫描目标失败：", e);
            return ApiResponse.error("创建失败");
        }
    }

    @PutMapping("/update")
    public ApiResponse<ScanTargetEntity> update(@RequestBody ScanTargetEntity entity) {
        try {
            ScanTargetEntity updated = scanTargetService.update(entity);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新扫描目标失败：", e);
            return ApiResponse.error("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        try {
            boolean success = scanTargetService.delete(id);
            if (success) {
                return ApiResponse.success(true);
            } else {
                return ApiResponse.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除扫描目标失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    @PutMapping("/{id}/enabled")
    public ApiResponse<Boolean> updateEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        try {
            boolean success = scanTargetService.updateEnabled(id, enabled);
            if (success) {
                return ApiResponse.success(true);
            } else {
                return ApiResponse.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新扫描目标启用状态失败：", e);
            return ApiResponse.error("更新失败");
        }
    }
}
