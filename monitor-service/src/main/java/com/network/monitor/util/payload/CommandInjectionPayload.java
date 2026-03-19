package com.network.monitor.util.payload;

import java.util.List;

public final class CommandInjectionPayload {

    private CommandInjectionPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        if ("FULL".equalsIgnoreCase(scanType)) {
            return List.of(
                    new PayloadCase("echo scan_cmd_quick", "scan_cmd_quick", "命令执行回显探测"),
                    new PayloadCase("echo scan_cmd_full", "scan_cmd_full", "命令执行二次回显探测")
            );
        }
        return List.of(
                new PayloadCase("echo scan_cmd_quick", "scan_cmd_quick", "命令执行回显探测")
        );
    }
}
