package com.network.monitor.util.template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FixSuggestionTemplate {

    private FixSuggestionTemplate() {
    }

    private static final Map<String, FixTemplate> TEMPLATES = createTemplates();

    private static Map<String, FixTemplate> createTemplates() {
        Map<String, FixTemplate> templates = new HashMap<>();
        
        templates.put("SQL_INJECTION", new FixTemplate(
            "SQL注入防护",
            List.of(
                "使用参数化查询或预编译语句（PreparedStatement）",
                "对所有用户输入进行严格校验和过滤",
                "使用ORM框架避免直接拼接SQL",
                "限制数据库用户权限，遵循最小权限原则",
                "启用Web应用防火墙（WAF）的SQL注入防护规则"
            ),
            List.of(
                "Java: 使用PreparedStatement: SELECT * FROM users WHERE id = ?",
                "MyBatis: 使用#{param}而非${param}",
                "Spring Data JPA: 使用@Query注解的参数绑定"
            )
        ));
        
        templates.put("XSS", new FixTemplate(
            "跨站脚本防护",
            List.of(
                "对所有输出进行HTML实体编码",
                "使用安全的渲染方式，避免innerHTML",
                "实施内容安全策略（CSP）",
                "对URL参数进行严格校验",
                "使用HTTPOnly和Secure Cookie标志"
            ),
            List.of(
                "Java: 使用OWASP ESAPI编码器",
                "前端: 使用textContent而非innerHTML",
                "响应头: Content-Security-Policy: default-src 'self'"
            )
        ));
        
        templates.put("COMMAND_INJECTION", new FixTemplate(
            "命令注入防护",
            List.of(
                "避免直接执行用户输入的命令",
                "使用命令白名单机制",
                "使用安全的API替代系统命令",
                "对所有输入进行严格校验",
                "使用沙箱环境执行命令"
            ),
            List.of(
                "Java: 使用ProcessBuilder并设置严格的命令参数",
                "避免: Runtime.exec(userInput)",
                "推荐: 使用Java原生API替代系统命令"
            )
        ));
        
        templates.put("PATH_TRAVERSAL", new FixTemplate(
            "路径遍历防护",
            List.of(
                "对文件路径进行规范化处理",
                "使用白名单验证允许访问的文件",
                "禁止用户输入直接拼接到文件路径",
                "检查路径是否在允许的目录范围内",
                "使用安全的文件访问API"
            ),
            List.of(
                "Java: 使用Paths.get().normalize()规范化路径",
                "验证: path.startsWith(allowedDirectory)",
                "避免: new File(userInput)"
            )
        ));
        
        templates.put("FILE_INCLUSION", new FixTemplate(
            "文件包含漏洞防护",
            List.of(
                "限制可加载的文件类型",
                "使用白名单验证文件路径",
                "禁止加载外部文件",
                "对文件路径进行严格校验",
                "使用安全的资源加载机制"
            ),
            List.of(
                "Java: 使用ClassLoader.getResourceAsStream()",
                "验证: 只允许加载预定义的资源",
                "配置: 设置allow-url-include为false"
            )
        ));
        
        templates.put("SSRF", new FixTemplate(
            "服务端请求伪造防护",
            List.of(
                "验证和限制请求的目标URL",
                "使用白名单验证允许访问的域名",
                "禁止访问内网IP地址",
                "限制请求协议（仅允许HTTP/HTTPS）",
                "使用代理服务器隔离请求"
            ),
            List.of(
                "Java: 使用URL白名单验证",
                "验证: 禁止访问10.x.x.x、172.16.x.x、192.168.x.x",
                "限制: 只允许HTTP和HTTPS协议"
            )
        ));
        
        templates.put("XXE", new FixTemplate(
            "XML外部实体防护",
            List.of(
                "禁用XML外部实体解析",
                "禁用DTD处理",
                "使用安全的XML解析器配置",
                "对XML输入进行严格校验",
                "使用JSON替代XML"
            ),
            List.of(
                "Java: 设置XML解析器禁用外部实体",
                "配置: setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true)",
                "推荐: 使用Jackson/Gson处理JSON"
            )
        ));
        
        templates.put("CSRF", new FixTemplate(
            "跨站请求伪造防护",
            List.of(
                "实施CSRF Token验证",
                "验证HTTP Referer头",
                "使用SameSite Cookie属性",
                "对关键操作要求二次确认",
                "使用自定义请求头"
            ),
            List.of(
                "Spring Security: 启用csrf()配置",
                "Cookie: Set-Cookie: sessionId=xxx; SameSite=Strict",
                "前端: 在请求头中添加X-Requested-With"
            )
        ));
        
        return templates;
    }

    public static String generateFixSuggestion(String vulnType, String vulnPath) {
        FixTemplate template = TEMPLATES.get(vulnType);
        if (template == null) {
            return generateGenericFix(vulnPath);
        }
        
        StringBuilder suggestion = new StringBuilder();
        suggestion.append("【").append(template.getTitle()).append("】\n\n");
        suggestion.append("针对接口 ").append(vulnPath).append(" 的修复建议：\n\n");
        
        suggestion.append("核心措施：\n");
        for (int i = 0; i < template.getCoreMeasures().size(); i++) {
            suggestion.append(String.format("%d. %s\n", i + 1, template.getCoreMeasures().get(i)));
        }
        
        suggestion.append("\n代码示例：\n");
        for (String example : template.getCodeExamples()) {
            suggestion.append("• ").append(example).append("\n");
        }
        
        return suggestion.toString();
    }

    private static String generateGenericFix(String vulnPath) {
        StringBuilder suggestion = new StringBuilder();
        suggestion.append("【通用安全加固建议】\n\n");
        suggestion.append("针对接口 ").append(vulnPath).append(" 的修复建议：\n\n");
        suggestion.append("核心措施：\n");
        suggestion.append("1. 对所有用户输入进行严格校验\n");
        suggestion.append("2. 实施最小权限原则\n");
        suggestion.append("3. 使用安全的编码实践\n");
        suggestion.append("4. 定期进行安全审计\n");
        suggestion.append("5. 启用Web应用防火墙（WAF）\n");
        return suggestion.toString();
    }

    private static final class FixTemplate {
        private final String title;
        private final List<String> coreMeasures;
        private final List<String> codeExamples;

        public FixTemplate(String title, List<String> coreMeasures, List<String> codeExamples) {
            this.title = title;
            this.coreMeasures = coreMeasures;
            this.codeExamples = codeExamples;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getCoreMeasures() {
            return coreMeasures;
        }

        public List<String> getCodeExamples() {
            return codeExamples;
        }
    }
}
