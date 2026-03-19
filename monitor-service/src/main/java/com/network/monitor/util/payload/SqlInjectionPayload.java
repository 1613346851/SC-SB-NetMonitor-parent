package com.network.monitor.util.payload;

import java.util.List;

public final class SqlInjectionPayload {

    private SqlInjectionPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        if ("FULL".equalsIgnoreCase(scanType)) {
            return List.of(
                    new PayloadCase("1 OR 1=1", "OR 1=1", "布尔型恒真注入探测"),
                    new PayloadCase("1; SELECT 1", "statement_results", "堆叠语句执行探测")
            );
        }
        return List.of(
                new PayloadCase("1 OR 1=1", "OR 1=1", "布尔型恒真注入探测")
        );
    }
}
