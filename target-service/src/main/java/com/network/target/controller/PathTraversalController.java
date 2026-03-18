package com.network.target.controller;

import com.network.target.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路径遍历漏洞测试接口
 * 核心：模拟文件读取功能，用户输入文件名直接拼接路径读取
 */
@RestController
@RequestMapping("/target/path")
@Slf4j
public class PathTraversalController {

    private final ResourceLoader resourceLoader;

    public PathTraversalController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 漏洞接口：直接拼接用户输入的文件名，无任何过滤
     * 攻击场景：用户可以使用 ../ 读取任意文件
     */
    @GetMapping("/read")
    public ApiResponse readFileVulnerable(@RequestParam("filename") String filename) {
        try {
            log.warn("【高危路径遍历漏洞】尝试读取文件：{}", filename);

            String baseDir = System.getProperty("user.dir");
            log.info("当前工作目录: {}", baseDir);

            String[] possibleTestFilesPaths = getTestFilesPaths(baseDir);
            
            for (String testFilesPath : possibleTestFilesPaths) {
                Path basePath = Paths.get(testFilesPath);
                Path filePath = basePath.resolve(filename).normalize();
                
                log.info("尝试路径: {}", filePath);
                
                File file = filePath.toFile();
                if (file.exists() && file.isFile()) {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    log.warn("【路径遍历漏洞触发】成功读取文件：{}, 实际路径：{}", filename, file.getAbsolutePath());

                    return ApiResponse.success()
                            .message("文件读取成功（漏洞接口）")
                            .data("filename", filename)
                            .data("content", content)
                            .data("file_path", file.getAbsolutePath())
                            .data("warning", "路径遍历漏洞：未对文件名进行任何过滤，可读取任意文件！");
                }
            }

            try {
                Resource resource = resourceLoader.getResource("classpath:static/test-files/" + filename);
                if (resource.exists()) {
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    log.warn("【路径遍历漏洞触发】成功读取文件：{}", filename);
                    return ApiResponse.success()
                            .message("文件读取成功（漏洞接口）")
                            .data("filename", filename)
                            .data("content", content)
                            .data("file_path", "classpath:static/test-files/" + filename)
                            .data("warning", "路径遍历漏洞：未对文件名进行任何过滤，可读取任意文件！");
                }
            } catch (Exception ignored) {
            }

            Path attemptedPath = Paths.get(possibleTestFilesPaths[0]).resolve(filename).normalize();
            return ApiResponse.error()
                    .message("文件不存在")
                    .data("filename", filename)
                    .data("attempted_path", attemptedPath.toString())
                    .data("working_dir", baseDir);

        } catch (IOException e) {
            log.error("文件读取异常", e);
            return ApiResponse.error()
                    .message("文件读取失败：" + e.getMessage())
                    .data("filename", filename);
        }
    }

    private String[] getTestFilesPaths(String baseDir) {
        return new String[] {
            baseDir + "/target-service/src/main/resources/static/test-files",
            baseDir + "/src/main/resources/static/test-files",
            baseDir + "/src/main/resources/static/test-files",
            "src/main/resources/static/test-files"
        };
    }

    /**
     * 安全接口：对文件名进行白名单校验和路径规范化
     * 防护措施：
     * 1. 过滤 ../ 等危险字符
     * 2. 路径规范化后检查是否在允许目录内
     * 3. 文件名白名单校验
     */
    @GetMapping("/safe-read")
    public ApiResponse readFileSafe(@RequestParam("filename") String filename) {
        try {
            log.info("【安全接口】尝试读取文件：{}", filename);

            if (filename == null || filename.trim().isEmpty()) {
                return ApiResponse.error().message("文件名不能为空");
            }

            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                log.warn("【安全拦截】检测到非法路径字符：{}", filename);
                return ApiResponse.error()
                        .message("非法文件名：禁止使用路径遍历字符")
                        .data("filename", filename)
                        .data("blocked_reason", "文件名包含非法字符（.. / \\）");
            }

            List<String> allowedFiles = List.of(
                "test.txt",
                "config/test.properties",
                "readme.txt",
                "sample.txt"
            );

            boolean isAllowed = allowedFiles.stream()
                    .anyMatch(allowed -> allowed.equals(filename));

            if (!isAllowed) {
                log.warn("【安全拦截】文件不在白名单中：{}", filename);
                return ApiResponse.error()
                        .message("文件不在允许列表中")
                        .data("filename", filename)
                        .data("allowed_files", allowedFiles);
            }

            Resource resource = resourceLoader.getResource("classpath:static/test-files/" + filename);
            if (!resource.exists()) {
                return ApiResponse.error()
                        .message("文件不存在")
                        .data("filename", filename);
            }

            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("【安全接口】成功读取文件：{}", filename);

            return ApiResponse.success()
                    .message("文件读取成功（安全接口）")
                    .data("filename", filename)
                    .data("content", content)
                    .data("file_path", "classpath:static/test-files/" + filename)
                    .data("security_note", "已通过白名单校验和路径过滤");

        } catch (IOException e) {
            log.error("安全文件读取异常", e);
            return ApiResponse.error()
                    .message("文件读取失败：" + e.getMessage());
        }
    }

    /**
     * 获取允许读取的文件列表
     */
    @GetMapping("/list-files")
    public ApiResponse listAllowedFiles() {
        List<Map<String, String>> files = new ArrayList<>();
        
        files.add(createFileInfo("test.txt", "基础测试文本文件", "txt"));
        files.add(createFileInfo("config/test.properties", "配置文件示例", "properties"));
        files.add(createFileInfo("readme.txt", "说明文档", "txt"));

        return ApiResponse.success()
                .message("获取文件列表成功")
                .data("files", files)
                .data("base_path", "/target/path/read?filename=");
    }

    private Map<String, String> createFileInfo(String name, String desc, String type) {
        Map<String, String> info = new HashMap<>();
        info.put("name", name);
        info.put("description", desc);
        info.put("type", type);
        return info;
    }
}
