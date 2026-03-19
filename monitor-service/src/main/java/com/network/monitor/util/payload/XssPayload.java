package com.network.monitor.util.payload;

import java.util.List;

public final class XssPayload {

    private XssPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        if ("FULL".equalsIgnoreCase(scanType)) {
            return List.of(
                    new PayloadCase("<svg/onload=alert('scan_xss_quick')>", "scan_xss_quick", "SVG 事件回显探测"),
                    new PayloadCase("<img src=x onerror=alert('scan_xss_full')>", "scan_xss_full", "IMG onerror 回显探测")
            );
        }
        return List.of(
                new PayloadCase("<svg/onload=alert('scan_xss_quick')>", "scan_xss_quick", "SVG 事件回显探测")
        );
    }
}
