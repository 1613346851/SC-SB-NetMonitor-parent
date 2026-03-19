package com.network.target.controller;

import com.network.target.common.ApiResponse;
import com.network.target.entity.XxeLogEntity;
import com.network.target.repository.XxeLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/target/xxe")
@Slf4j
public class XxeController {

    private final XxeLogRepository xxeLogRepository;

    public XxeController(XxeLogRepository xxeLogRepository) {
        this.xxeLogRepository = xxeLogRepository;
    }

    @PostMapping("/parse")
    public ApiResponse parseXmlVulnerable(@RequestBody String xmlContent) {
        try {
            log.warn("【高危XXE漏洞】尝试解析XML：{}", xmlContent.substring(0, Math.min(200, xmlContent.length())));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            Map<String, Object> result = parseDocument(document);

            boolean hasExternalEntity = xmlContent.contains("<!ENTITY") || xmlContent.contains("SYSTEM") || xmlContent.contains("PUBLIC");
            log.warn("【XXE漏洞触发】XML解析完成，包含外部实体：{}", hasExternalEntity);

            XxeLogEntity logEntity = new XxeLogEntity();
            logEntity.setXmlContent(xmlContent.length() > 5000 ? xmlContent.substring(0, 5000) : xmlContent);
            logEntity.setParseResult(result.toString());
            logEntity.setHasExternalEntity(hasExternalEntity);
            xxeLogRepository.save(logEntity);
            log.info("【数据库存储】已将XXE解析日志存储到数据库");

            return ApiResponse.success()
                    .message("XML解析成功（漏洞接口）")
                    .data("parsed_data", result)
                    .data("has_external_entity", hasExternalEntity)
                    .data("db_stored", true)
                    .data("warning", "XXE漏洞：未禁用外部实体解析，可读取服务器文件！");

        } catch (Exception e) {
            log.error("XXE解析异常", e);
            
            XxeLogEntity logEntity = new XxeLogEntity();
            logEntity.setXmlContent(xmlContent.length() > 5000 ? xmlContent.substring(0, 5000) : xmlContent);
            logEntity.setParseResult("解析失败：" + e.getMessage());
            logEntity.setHasExternalEntity(xmlContent.contains("<!ENTITY") || xmlContent.contains("SYSTEM"));
            xxeLogRepository.save(logEntity);
            
            return ApiResponse.error()
                    .message("XML解析失败：" + e.getMessage())
                    .data("error_type", e.getClass().getSimpleName());
        }
    }

    @PostMapping("/safe-parse")
    public ApiResponse parseXmlSafe(@RequestBody String xmlContent) {
        try {
            log.info("【安全接口】尝试解析XML：{}", xmlContent.substring(0, Math.min(200, xmlContent.length())));

            if (xmlContent.contains("<!ENTITY") || xmlContent.contains("SYSTEM") || xmlContent.contains("PUBLIC")) {
                log.warn("【安全拦截】检测到外部实体声明");
                return ApiResponse.error()
                        .message("检测到外部实体声明，已拦截")
                        .data("blocked_reason", "XML包含外部实体声明（ENTITY/SYSTEM/PUBLIC）");
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            Map<String, Object> result = parseDocument(document);

            log.info("【安全接口】XML解析完成");

            return ApiResponse.success()
                    .message("XML解析成功（安全接口）")
                    .data("parsed_data", result)
                    .data("security_note", "已禁用DTD和外部实体解析");

        } catch (Exception e) {
            log.error("安全XXE解析异常", e);
            return ApiResponse.error()
                    .message("XML解析失败：" + e.getMessage());
        }
    }

    @GetMapping("/test-cases")
    public ApiResponse getTestCases() {
        Map<String, String> testCases = new HashMap<>();
        
        testCases.put("normal", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<user>\n" +
                "    <name>张三</name>\n" +
                "    <email>zhangsan@example.com</email>\n" +
                "    <age>25</age>\n" +
                "</user>");

        String baseDir = System.getProperty("user.dir");
        String testFilePath = baseDir + "/target-service/src/main/resources/static/test-files/test.txt";
        testFilePath = testFilePath.replace("\\", "/");
        
        testCases.put("xxe_file_read", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE user [\n" +
                "    <!ENTITY xxe SYSTEM \"file:///" + testFilePath + "\">\n" +
                "]>\n" +
                "<user>\n" +
                "    <name>&xxe;</name>\n" +
                "    <email>test@example.com</email>\n" +
                "</user>");

        String projectConfigPath = baseDir + "/target-service/src/main/resources/static/test-files/config/test.properties";
        projectConfigPath = projectConfigPath.replace("\\", "/");

        testCases.put("xxe_project_config", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE user [\n" +
                "    <!ENTITY xxe SYSTEM \"file:///" + projectConfigPath + "\">\n" +
                "]>\n" +
                "<user>\n" +
                "    <name>&xxe;</name>\n" +
                "    <email>test@example.com</email>\n" +
                "</user>");

        String pomPath = baseDir + "/target-service/pom.xml";
        pomPath = pomPath.replace("\\", "/");
        
        testCases.put("xxe_project_file", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE user [\n" +
                "    <!ENTITY xxe SYSTEM \"file:///" + pomPath + "\">\n" +
                "]>\n" +
                "<user>\n" +
                "    <name>&xxe;</name>\n" +
                "    <email>test@example.com</email>\n" +
                "</user>");

        return ApiResponse.success()
                .message("获取测试用例成功")
                .data("test_cases", testCases);
    }

    @GetMapping("/logs")
    public ApiResponse getXxeLogs() {
        List<XxeLogEntity> logs = xxeLogRepository.findAll();
        return ApiResponse.success()
                .message("获取XXE日志成功")
                .data("logs", logs)
                .data("total", logs.size());
    }

    @DeleteMapping("/logs")
    public ApiResponse clearXxeLogs() {
        int deleted = xxeLogRepository.deleteAll();
        return ApiResponse.success()
                .message("已清空XXE日志")
                .data("deleted_count", deleted);
    }

    @GetMapping("/logs/count")
    public ApiResponse getXxeLogsCount() {
        long count = xxeLogRepository.count();
        return ApiResponse.success()
                .message("获取XXE日志数量成功")
                .data("count", count);
    }

    private Map<String, Object> parseDocument(Document document) {
        Map<String, Object> result = new HashMap<>();
        Element root = document.getDocumentElement();
        result.put("root_element", root.getNodeName());

        NodeList children = root.getChildNodes();
        Map<String, String> elements = new HashMap<>();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element element = (Element) children.item(i);
                elements.put(element.getNodeName(), element.getTextContent());
            }
        }
        result.put("elements", elements);

        return result;
    }
}
