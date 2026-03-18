package com.network.target.controller;

import com.network.target.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Map;

/**
 * XXE XML外部实体注入漏洞测试接口
 * 核心：模拟XML数据提交解析功能，使用默认不安全解析器
 */
@RestController
@RequestMapping("/target/xxe")
@Slf4j
public class XxeController {

    /**
     * 漏洞接口：未禁用外部实体的XML解析
     * 攻击场景：攻击者可构造恶意XML读取服务器文件
     */
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

            return ApiResponse.success()
                    .message("XML解析成功（漏洞接口）")
                    .data("parsed_data", result)
                    .data("has_external_entity", hasExternalEntity)
                    .data("warning", "XXE漏洞：未禁用外部实体解析，可读取服务器文件！");

        } catch (Exception e) {
            log.error("XXE解析异常", e);
            return ApiResponse.error()
                    .message("XML解析失败：" + e.getMessage())
                    .data("error_type", e.getClass().getSimpleName());
        }
    }

    /**
     * 安全接口：禁用外部实体、安全解析器
     * 防护措施：
     * 1. 禁用DTD
     * 2. 禁用外部实体
     * 3. 禁用外部参数实体
     */
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

    /**
     * 获取测试用例
     */
    @GetMapping("/test-cases")
    public ApiResponse getTestCases() {
        Map<String, String> testCases = new HashMap<>();
        
        testCases.put("normal", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<user>\n" +
                "    <name>张三</name>\n" +
                "    <email>zhangsan@example.com</email>\n" +
                "    <age>25</age>\n" +
                "</user>");

        testCases.put("xxe_file_read", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE user [\n" +
                "    <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<user>\n" +
                "    <name>&xxe;</name>\n" +
                "    <email>test@example.com</email>\n" +
                "</user>");

        testCases.put("xxe_windows", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE user [\n" +
                "    <!ENTITY xxe SYSTEM \"file:///C:/Windows/win.ini\">\n" +
                "]>\n" +
                "<user>\n" +
                "    <name>&xxe;</name>\n" +
                "    <email>test@example.com</email>\n" +
                "</user>");

        testCases.put("xxe_project_file", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE user [\n" +
                "    <!ENTITY xxe SYSTEM \"file:./pom.xml\">\n" +
                "]>\n" +
                "<user>\n" +
                "    <name>&xxe;</name>\n" +
                "    <email>test@example.com</email>\n" +
                "</user>");

        return ApiResponse.success()
                .message("获取测试用例成功")
                .data("test_cases", testCases);
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
