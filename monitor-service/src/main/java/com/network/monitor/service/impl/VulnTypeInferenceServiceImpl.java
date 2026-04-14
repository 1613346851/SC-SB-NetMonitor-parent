package com.network.monitor.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network.monitor.dto.InputParamDTO;
import com.network.monitor.dto.InterfaceFeatureDTO;
import com.network.monitor.dto.VulnInferenceResult;
import com.network.monitor.entity.ScanInterfaceEntity;
import com.network.monitor.mapper.ScanInterfaceMapper;
import com.network.monitor.service.VulnTypeInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 漏洞类型推断服务实现
 * 细粒度推断：每个漏洞类型对应多个具体规则（漏洞）
 */
@Slf4j
@Service
public class VulnTypeInferenceServiceImpl implements VulnTypeInferenceService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ScanInterfaceMapper scanInterfaceMapper;

    @Override
    public List<VulnInferenceResult> inferVulnTypes(InterfaceFeatureDTO feature) {
        List<VulnInferenceResult> results = new ArrayList<>();

        if (feature == null) {
            return results;
        }

        inferSqlInjection(feature, results);
        inferXss(feature, results);
        inferCommandInjection(feature, results);
        inferPathTraversal(feature, results);
        inferFileInclusion(feature, results);
        inferSsrf(feature, results);
        inferXxe(feature, results);
        inferCsrf(feature, results);
        inferDeserialization(feature, results);
        inferFileUpload(feature, results);

        return results;
    }

    @Override
    public List<VulnInferenceResult> inferVulnTypes(ScanInterfaceEntity entity) {
        InterfaceFeatureDTO feature = convertToFeature(entity);
        return inferVulnTypes(feature);
    }

    @Override
    public List<VulnInferenceResult> inferAndUpdate(Long interfaceId) {
        ScanInterfaceEntity entity = scanInterfaceMapper.selectById(interfaceId);
        if (entity == null) {
            return new ArrayList<>();
        }

        List<VulnInferenceResult> results = inferVulnTypes(entity);
        
        List<String> vulnTypes = results.stream()
                .map(VulnInferenceResult::getVulnType)
                .toList();

        try {
            String inferredVulnTypesJson = objectMapper.writeValueAsString(vulnTypes);
            entity.setInferredVulnTypes(inferredVulnTypesJson);
            scanInterfaceMapper.update(entity);
            log.info("更新接口推断漏洞类型：interfaceId={}, vulnTypes={}", interfaceId, vulnTypes);
        } catch (JsonProcessingException e) {
            log.error("序列化推断漏洞类型失败", e);
        }

        return results;
    }

    private InterfaceFeatureDTO convertToFeature(ScanInterfaceEntity entity) {
        InterfaceFeatureDTO feature = new InterfaceFeatureDTO();
        feature.setBusinessType(entity.getBusinessType());
        feature.setHttpMethod(entity.getHttpMethod());
        feature.setOutputType(entity.getOutputType());
        feature.setAuthRequired(entity.getAuthRequired());
        feature.setContentType(entity.getContentType());
        feature.setExternalRequest(entity.getExternalRequest());
        feature.setFileOperation(entity.getFileOperation());
        feature.setDbOperation(entity.getDbOperation());

        if (entity.getInputParams() != null && !entity.getInputParams().isEmpty()) {
            try {
                List<InputParamDTO> params = objectMapper.readValue(
                        entity.getInputParams(),
                        new TypeReference<List<InputParamDTO>>() {}
                );
                feature.setInputParams(params);
            } catch (JsonProcessingException e) {
                log.warn("解析输入参数失败：{}", e.getMessage());
            }
        }

        return feature;
    }

    private void inferSqlInjection(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if (hasUserInput(feature) && isTrue(feature.getDbOperation())) {
            List<Long> ruleIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
            results.add(new VulnInferenceResult("SQL_INJECTION",
                    "接口涉及数据库操作且存在用户输入参数，可能存在多种SQL注入漏洞",
                    ruleIds));
        }
    }

    private void inferXss(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if (hasUserInput(feature)) {
            if ("HTML".equals(feature.getOutputType())) {
                List<Long> ruleIds = Arrays.asList(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
                results.add(new VulnInferenceResult("XSS",
                        "用户输入可能被直接渲染到HTML响应中，可能存在多种XSS攻击向量",
                        ruleIds));
            } else if ("DATA_SUBMIT".equals(feature.getBusinessType())) {
                List<Long> ruleIds = Arrays.asList(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
                results.add(new VulnInferenceResult("XSS",
                        "数据提交接口可能存在存储型XSS，支持多种XSS攻击向量",
                        ruleIds));
            }
        }
    }

    private void inferCommandInjection(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if ("COMMAND_EXEC".equals(feature.getBusinessType())) {
            List<Long> ruleIds = Arrays.asList(21L, 22L, 23L, 24L, 25L, 26L, 27L, 28L, 29L);
            results.add(new VulnInferenceResult("COMMAND_INJECTION",
                    "接口执行系统命令，可能存在多种命令注入方式",
                    ruleIds));
        }
    }

    private void inferPathTraversal(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if ("FILE_OPERATION".equals(feature.getBusinessType()) && hasUserInput(feature)) {
            List<Long> ruleIds = Arrays.asList(30L, 31L, 32L, 33L, 34L, 35L);
            results.add(new VulnInferenceResult("PATH_TRAVERSAL",
                    "文件操作接口存在用户可控路径，可能存在多种路径遍历攻击方式",
                    ruleIds));
        }
    }

    private void inferFileInclusion(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if ("FILE_OPERATION".equals(feature.getBusinessType()) && hasPathParam(feature)) {
            List<Long> ruleIds = Arrays.asList(36L, 37L, 38L, 39L, 40L);
            results.add(new VulnInferenceResult("FILE_INCLUSION",
                    "接口动态加载文件资源，可能存在多种文件包含攻击方式",
                    ruleIds));
        }
    }

    private void inferSsrf(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if ("URL_FETCH".equals(feature.getBusinessType()) || isTrue(feature.getExternalRequest())) {
            List<Long> ruleIds = Arrays.asList(41L, 42L, 43L, 44L, 45L, 46L);
            results.add(new VulnInferenceResult("SSRF",
                    "接口发起外部URL请求，可能存在多种SSRF攻击方式",
                    ruleIds));
        }
    }

    private void inferXxe(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if ("XML_PROCESS".equals(feature.getBusinessType()) ||
                "application/xml".equals(feature.getContentType())) {
            List<Long> ruleIds = Arrays.asList(47L, 48L, 49L, 50L, 51L, 52L);
            results.add(new VulnInferenceResult("XXE",
                    "接口处理XML数据，可能存在多种XXE攻击方式",
                    ruleIds));
        }
    }

    private void inferCsrf(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if (isStateChangingMethod(feature.getHttpMethod()) && !isTrue(feature.getAuthRequired())) {
            List<Long> ruleIds = Arrays.asList(57L, 58L, 59L);
            results.add(new VulnInferenceResult("CSRF",
                    "状态变更接口无需认证保护，可能存在多种CSRF攻击方式",
                    ruleIds));
        }
    }

    private void inferDeserialization(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        String contentType = feature.getContentType();
        if (contentType != null && (contentType.contains("serialized") ||
                contentType.contains("java-serialized"))) {
            List<Long> ruleIds = Arrays.asList(53L, 54L, 55L, 56L);
            results.add(new VulnInferenceResult("DESERIALIZATION",
                    "接口处理序列化数据，可能存在多种反序列化攻击方式",
                    ruleIds));
        }
        if ("USER_INPUT".equals(feature.getBusinessType()) && hasSerializedParam(feature)) {
            List<Long> ruleIds = Arrays.asList(53L, 54L, 55L, 56L);
            results.add(new VulnInferenceResult("DESERIALIZATION",
                    "接口可能处理序列化数据，存在反序列化攻击风险",
                    ruleIds));
        }
    }

    private void inferFileUpload(InterfaceFeatureDTO feature, List<VulnInferenceResult> results) {
        if ("FILE_UPLOAD".equals(feature.getBusinessType())) {
            results.add(new VulnInferenceResult("FILE_UPLOAD",
                    "文件上传功能",
                    null));
        }
        String contentType = feature.getContentType();
        if (contentType != null && contentType.contains("multipart/form-data")) {
            results.add(new VulnInferenceResult("FILE_UPLOAD",
                    "接口支持文件上传",
                    null));
        }
    }

    private boolean hasUserInput(InterfaceFeatureDTO feature) {
        return feature.getInputParams() != null && !feature.getInputParams().isEmpty();
    }

    private boolean hasPathParam(InterfaceFeatureDTO feature) {
        if (feature.getInputParams() == null) {
            return false;
        }
        return feature.getInputParams().stream()
                .anyMatch(p -> "path".equalsIgnoreCase(p.getName()) ||
                        "file".equalsIgnoreCase(p.getName()) ||
                        "filename".equalsIgnoreCase(p.getName()));
    }

    private boolean hasSerializedParam(InterfaceFeatureDTO feature) {
        if (feature.getInputParams() == null) {
            return false;
        }
        return feature.getInputParams().stream()
                .anyMatch(p -> p.getName() != null && 
                        (p.getName().toLowerCase().contains("serialized") ||
                         p.getName().toLowerCase().contains("data")));
    }

    private boolean isStateChangingMethod(String method) {
        if (method == null) {
            return false;
        }
        String upper = method.toUpperCase();
        return "POST".equals(upper) || "PUT".equals(upper) ||
                "DELETE".equals(upper) || "PATCH".equals(upper);
    }

    private boolean isTrue(Integer value) {
        return value != null && value == 1;
    }
}
