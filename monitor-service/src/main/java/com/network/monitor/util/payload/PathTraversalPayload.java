package com.network.monitor.util.payload;

import java.util.List;

public final class PathTraversalPayload {

    private PathTraversalPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        if ("FULL".equalsIgnoreCase(scanType)) {
            return List.of(
                    new PayloadCase("../../application.yml", "server:", "读取服务配置文件"),
                    new PayloadCase("../../../../../pom.xml", "<project", "读取项目构建文件")
            );
        }
        return List.of(
                new PayloadCase("../../application.yml", "server:", "读取服务配置文件")
        );
    }
}
