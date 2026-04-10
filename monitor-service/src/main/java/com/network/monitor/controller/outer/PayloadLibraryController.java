package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.PayloadLibraryEntity;
import com.network.monitor.service.PayloadLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payload-library")
public class PayloadLibraryController {

    @Autowired
    private PayloadLibraryService payloadLibraryService;

    @GetMapping("/{id}")
    public ApiResponse<PayloadLibraryEntity> getById(@PathVariable Long id) {
        try {
            PayloadLibraryEntity entity = payloadLibraryService.getById(id);
            if (entity == null) {
                return ApiResponse.notFound("Payload不存在");
            }
            return ApiResponse.success(entity);
        } catch (Exception e) {
            log.error("查询Payload详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getList(
            @RequestParam(required = false) String vulnType,
            @RequestParam(required = false) String payloadLevel,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            if (page < 1) page = 1;
            if (size < 1 || size > 100) size = 10;

            Map<String, Object> result = payloadLibraryService.getPage(vulnType, payloadLevel, description, enabled, page, size);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询Payload列表失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/vuln-type/{vulnType}")
    public ApiResponse<List<PayloadLibraryEntity>> getByVulnType(@PathVariable String vulnType) {
        try {
            List<PayloadLibraryEntity> list = payloadLibraryService.getByVulnType(vulnType);
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询漏洞类型Payload失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/vuln-type/{vulnType}/level/{payloadLevel}")
    public ApiResponse<List<PayloadLibraryEntity>> getByVulnTypeAndLevel(
            @PathVariable String vulnType,
            @PathVariable String payloadLevel) {
        try {
            List<PayloadLibraryEntity> list = payloadLibraryService.getByVulnTypeAndLevel(vulnType, payloadLevel);
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询漏洞类型和级别Payload失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/enabled")
    public ApiResponse<List<PayloadLibraryEntity>> getEnabled() {
        try {
            List<PayloadLibraryEntity> list = payloadLibraryService.getAllEnabled();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询启用Payload失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/all")
    public ApiResponse<List<PayloadLibraryEntity>> getAll() {
        try {
            List<PayloadLibraryEntity> list = payloadLibraryService.getAll();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询所有Payload失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @PostMapping("/add")
    public ApiResponse<PayloadLibraryEntity> add(@RequestBody PayloadLibraryEntity entity) {
        try {
            PayloadLibraryEntity created = payloadLibraryService.create(entity);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建Payload失败：", e);
            return ApiResponse.error("创建失败");
        }
    }

    @PutMapping("/update")
    public ApiResponse<PayloadLibraryEntity> update(@RequestBody PayloadLibraryEntity entity) {
        try {
            PayloadLibraryEntity updated = payloadLibraryService.update(entity);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新Payload失败：", e);
            return ApiResponse.error("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        try {
            boolean success = payloadLibraryService.delete(id);
            if (success) {
                return ApiResponse.success(true);
            } else {
                return ApiResponse.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除Payload失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    @PutMapping("/{id}/enabled")
    public ApiResponse<Boolean> updateEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        try {
            boolean success = payloadLibraryService.updateEnabled(id, enabled);
            if (success) {
                return ApiResponse.success(true);
            } else {
                return ApiResponse.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新Payload启用状态失败：", e);
            return ApiResponse.error("更新失败");
        }
    }
}
