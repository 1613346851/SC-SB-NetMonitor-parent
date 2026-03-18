package com.network.target.controller;

import com.network.target.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 文件包含漏洞测试接口
 * 核心：模拟动态加载配置文件功能，用户输入路径直接加载
 */
@RestController
@RequestMapping("/target/file")
@Slf4j
public class FileIncludeController {

    private final ResourceLoader resourceLoader;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("properties", "xml", "json", "txt");
    private static final List<String> ALLOWED_FILES = Arrays.asList(
            "config/test.properties",
            "config/app.properties",
            "test.txt"
    );

    public FileIncludeController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 漏洞接口：无校验加载用户指定的文件
     * 攻击场景：用户可以加载任意文件，包括敏感配置
     */
    @GetMapping("/include")
    public ApiResponse includeFileVulnerable(@RequestParam("path") String filePath) {
        try {
            log.warn("【高危文件包含漏洞】尝试加载文件：{}", filePath);

            String resourcePath = filePath;
            if (!filePath.startsWith("classpath:") && !filePath.startsWith("file:")) {
                resourcePath = "classpath:" + filePath;
            }

            Resource resource = resourceLoader.getResource(resourcePath);

            if (!resource.exists()) {
                resource = resourceLoader.getResource("classpath:static/test-files/" + filePath);
            }

            if (!resource.exists()) {
                log.warn("文件不存在：{}", filePath);
                return ApiResponse.error()
                        .message("文件不存在")
                        .data("requested_path", filePath);
            }

            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.warn("【文件包含漏洞触发】成功加载文件：{}", filePath);

            Map<String, Object> parsedContent = parseFileContent(filePath, content);

            return ApiResponse.success()
                    .message("文件加载成功（漏洞接口）")
                    .data("file_path", filePath)
                    .data("content", content)
                    .data("parsed_content", parsedContent)
                    .data("warning", "文件包含漏洞：未对文件路径和类型进行任何限制！");

        } catch (IOException e) {
            log.error("文件加载异常", e);
            return ApiResponse.error()
                    .message("文件加载失败：" + e.getMessage())
                    .data("requested_path", filePath);
        }
    }

    /**
     * 安全接口：文件类型白名单 + 路径限制
     * 防护措施：
     * 1. 仅允许特定扩展名
     * 2. 仅允许预定义的文件路径
     * 3. 路径规范化检查
     */
    @GetMapping("/safe-include")
    public ApiResponse includeFileSafe(@RequestParam("path") String filePath) {
        try {
            log.info("【安全接口】尝试加载文件：{}", filePath);

            if (filePath == null || filePath.trim().isEmpty()) {
                return ApiResponse.error().message("文件路径不能为空");
            }

            if (filePath.contains("..") || filePath.contains("~")) {
                log.warn("【安全拦截】检测到非法路径字符：{}", filePath);
                return ApiResponse.error()
                        .message("非法路径：禁止使用路径遍历字符")
                        .data("requested_path", filePath)
                        .data("blocked_reason", "路径包含非法字符（.. 或 ~）");
            }

            String extension = getFileExtension(filePath);
            if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
                log.warn("【安全拦截】不允许的文件类型：{}", extension);
                return ApiResponse.error()
                        .message("不允许的文件类型")
                        .data("requested_path", filePath)
                        .data("allowed_extensions", ALLOWED_EXTENSIONS);
            }

            String normalizedPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
            boolean isAllowed = ALLOWED_FILES.stream()
                    .anyMatch(allowed -> allowed.equals(normalizedPath) || normalizedPath.endsWith(allowed));

            if (!isAllowed) {
                log.warn("【安全拦截】文件不在白名单中：{}", filePath);
                return ApiResponse.error()
                        .message("文件不在允许列表中")
                        .data("requested_path", filePath)
                        .data("allowed_files", ALLOWED_FILES);
            }

            Resource resource = resourceLoader.getResource("classpath:" + normalizedPath);
            if (!resource.exists()) {
                resource = resourceLoader.getResource("classpath:static/test-files/" + normalizedPath);
            }

            if (!resource.exists()) {
                return ApiResponse.error()
                        .message("文件不存在")
                        .data("requested_path", filePath);
            }

            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> parsedContent = parseFileContent(filePath, content);

            log.info("【安全接口】成功加载文件：{}", filePath);

            return ApiResponse.success()
                    .message("文件加载成功（安全接口）")
                    .data("file_path", filePath)
                    .data("content", content)
                    .data("parsed_content", parsedContent)
                    .data("security_note", "已通过文件类型白名单和路径限制校验");

        } catch (IOException e) {
            log.error("安全文件加载异常", e);
            return ApiResponse.error()
                    .message("文件加载失败：" + e.getMessage());
        }
    }

    /**
     * 获取允许加载的文件列表
     */
    @GetMapping("/list-allowed")
    public ApiResponse listAllowedFiles() {
        List<Map<String, String>> files = Arrays.asList(
                createFileInfo("config/test.properties", "测试配置文件", "properties", "包含数据库和应用配置"),
                createFileInfo("test.txt", "测试文本文件", "txt", "基础测试文本")
        );

        return ApiResponse.success()
                .message("获取允许文件列表成功")
                .data("files", files)
                .data("allowed_extensions", ALLOWED_EXTENSIONS);
    }

    private Map<String, Object> parseFileContent(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("raw_content", content);

        if (filePath.endsWith(".properties")) {
            try {
                Properties props = new Properties();
                props.load(new java.io.StringReader(content));
                Map<String, String> properties = new HashMap<>();
                props.forEach((k, v) -> properties.put(k.toString(), v.toString()));
                result.put("properties", properties);
            } catch (IOException e) {
                result.put("parse_error", e.getMessage());
            }
        }

        return result;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : null;
    }

    private Map<String, String> createFileInfo(String path, String name, String type, String desc) {
        Map<String, String> info = new HashMap<>();
        info.put("path", path);
        info.put("name", name);
        info.put("type", type);
        info.put("description", desc);
        return info;
    }
}
