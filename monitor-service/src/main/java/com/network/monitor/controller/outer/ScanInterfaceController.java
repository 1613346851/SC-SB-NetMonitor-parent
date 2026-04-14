package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.InterfaceFeatureDTO;
import com.network.monitor.dto.ScanInterfaceRelationDTO;
import com.network.monitor.dto.VulnInferenceResult;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.entity.ScanInterfaceEntity;
import com.network.monitor.entity.VulnerabilityMonitorEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import com.network.monitor.mapper.VulnerabilityMonitorMapper;
import com.network.monitor.service.ScanInterfaceService;
import com.network.monitor.service.VulnTypeInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/scan-interface")
public class ScanInterfaceController {

    @Autowired
    private ScanInterfaceService scanInterfaceService;

    @Autowired
    private VulnerabilityMonitorMapper vulnerabilityMonitorMapper;

    @Autowired
    private MonitorRuleMapper monitorRuleMapper;

    @Autowired
    private VulnTypeInferenceService vulnTypeInferenceService;

    @GetMapping("/{id}")
    public ApiResponse<ScanInterfaceEntity> getById(@PathVariable Long id) {
        try {
            ScanInterfaceEntity entity = scanInterfaceService.getById(id);
            if (entity == null) {
                return ApiResponse.notFound("扫描接口不存在");
            }
            return ApiResponse.success(entity);
        } catch (Exception e) {
            log.error("查询扫描接口详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getList(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String interfaceName,
            @RequestParam(required = false) String vulnType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            if (page < 1) page = 1;
            if (size < 1 || size > 100) size = 10;

            Map<String, Object> result = scanInterfaceService.getPage(targetId, interfaceName, vulnType, enabled, page, size);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询扫描接口列表失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/target/{targetId}")
    public ApiResponse<List<ScanInterfaceEntity>> getByTargetId(@PathVariable Long targetId) {
        try {
            List<ScanInterfaceEntity> list = scanInterfaceService.getByTargetId(targetId);
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询目标扫描接口失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/enabled")
    public ApiResponse<List<ScanInterfaceEntity>> getEnabled() {
        try {
            List<ScanInterfaceEntity> list = scanInterfaceService.getAllEnabled();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询启用扫描接口失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/all")
    public ApiResponse<List<ScanInterfaceEntity>> getAll() {
        try {
            List<ScanInterfaceEntity> list = scanInterfaceService.getAll();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询所有扫描接口失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/all-with-relations")
    public ApiResponse<List<ScanInterfaceRelationDTO>> getAllWithRelations() {
        try {
            List<ScanInterfaceRelationDTO> list = scanInterfaceService.getAllWithRelations();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询扫描接口关联信息失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/vuln-type/{vulnType}")
    public ApiResponse<List<ScanInterfaceEntity>> getByVulnType(@PathVariable String vulnType) {
        try {
            List<ScanInterfaceEntity> list = scanInterfaceService.getAllEnabled().stream()
                    .filter(item -> vulnType.equals(item.getVulnType()))
                    .toList();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询漏洞类型扫描接口失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @PostMapping("/add")
    public ApiResponse<ScanInterfaceEntity> add(@RequestBody ScanInterfaceEntity entity) {
        try {
            ScanInterfaceEntity created = scanInterfaceService.create(entity);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建扫描接口失败：", e);
            return ApiResponse.error("创建失败");
        }
    }

    @PutMapping("/update")
    public ApiResponse<ScanInterfaceEntity> update(@RequestBody ScanInterfaceEntity entity) {
        try {
            ScanInterfaceEntity updated = scanInterfaceService.update(entity);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新扫描接口失败：", e);
            return ApiResponse.error("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        try {
            boolean success = scanInterfaceService.delete(id);
            if (success) {
                return ApiResponse.success(true);
            } else {
                return ApiResponse.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除扫描接口失败：", e);
            return ApiResponse.error("删除失败");
        }
    }

    @PutMapping("/{id}/enabled")
    public ApiResponse<Boolean> updateEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        try {
            boolean success = scanInterfaceService.updateEnabled(id, enabled);
            if (success) {
                return ApiResponse.success(true);
            } else {
                return ApiResponse.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新扫描接口启用状态失败：", e);
            return ApiResponse.error("更新失败");
        }
    }

    @PutMapping("/{id}/defense-rule")
    public ApiResponse<Boolean> updateDefenseRule(
            @PathVariable Long id,
            @RequestParam Integer hasDefenseRule,
            @RequestParam(required = false) String defenseRuleNote) {
        try {
            boolean success = scanInterfaceService.updateDefenseRule(id, hasDefenseRule, defenseRuleNote);
            if (success) {
                return ApiResponse.success(true);
            } else {
                return ApiResponse.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新扫描接口防御规则标记失败：", e);
            return ApiResponse.error("更新失败");
        }
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable Long id) {
        try {
            ScanInterfaceEntity interfaceEntity = scanInterfaceService.getById(id);
            if (interfaceEntity == null) {
                return ApiResponse.notFound("扫描接口不存在");
            }

            List<VulnerabilityMonitorEntity> vulnList = vulnerabilityMonitorMapper.selectByVulnPath(interfaceEntity.getInterfacePath());

            String attackType = convertVulnTypeToAttackType(interfaceEntity.getVulnType());
            List<MonitorRuleEntity> ruleList = monitorRuleMapper.selectByAttackType(attackType);

            Map<String, Object> result = new HashMap<>();
            result.put("interface", interfaceEntity);
            result.put("vulnerabilities", vulnList);
            result.put("rules", ruleList);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询扫描接口详情失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    private String convertVulnTypeToAttackType(String vulnType) {
        if (vulnType == null) {
            return "UNKNOWN";
        }
        return switch (vulnType) {
            case "SQL_INJECTION" -> "SQL_INJECTION";
            case "XSS" -> "XSS";
            case "COMMAND_INJECTION" -> "COMMAND_INJECTION";
            case "PATH_TRAVERSAL" -> "PATH_TRAVERSAL";
            case "FILE_INCLUSION" -> "FILE_INCLUSION";
            case "SSRF" -> "SSRF";
            case "XXE" -> "XXE";
            case "CSRF" -> "CSRF";
            default -> vulnType;
        };
    }

    @PostMapping("/infer-vuln-types")
    public ApiResponse<List<VulnInferenceResult>> inferVulnTypes(@RequestBody InterfaceFeatureDTO feature) {
        List<VulnInferenceResult> results = vulnTypeInferenceService.inferVulnTypes(feature);
        return ApiResponse.success(results);
    }

    @PostMapping("/{id}/infer")
    public ApiResponse<List<VulnInferenceResult>> inferAndUpdateVulnTypes(@PathVariable Long id) {
        List<VulnInferenceResult> results = vulnTypeInferenceService.inferAndUpdate(id);
        return ApiResponse.success(results);
    }
}
