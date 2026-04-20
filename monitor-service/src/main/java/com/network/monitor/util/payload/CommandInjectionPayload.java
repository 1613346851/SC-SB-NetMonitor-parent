package com.network.monitor.util.payload;

import java.util.ArrayList;
import java.util.List;

public final class CommandInjectionPayload {

    private CommandInjectionPayload() {
    }

    public static List<PayloadCase> getPayloads(String scanType) {
        List<PayloadCase> payloads = new ArrayList<>();
        
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            payloads.add(new PayloadCase("ping 127.0.0.1 -n 2", "Pinging", "Windows Ping 命令探测"));
            
            if ("FULL".equalsIgnoreCase(scanType)) {
                payloads.add(new PayloadCase("whoami", "\\" , "Windows 用户身份探测"));
                payloads.add(new PayloadCase("dir", "bytes", "Windows 目录列表探测"));
                payloads.add(new PayloadCase("tasklist", "PID", "Windows 进程列表探测"));
                payloads.add(new PayloadCase("ipconfig", "IPv4", "Windows 网络配置探测"));
                payloads.add(new PayloadCase("net user", "account", "Windows 用户列表探测"));
                payloads.add(new PayloadCase("systeminfo", "OS", "Windows 系统信息探测"));
                payloads.add(new PayloadCase("type C:\\Windows\\win.ini", "[fonts]", "Windows 文件读取探测"));
            }
        } else {
            payloads.add(new PayloadCase("ping 127.0.0.1 -c 2", "ping statistics", "Linux Ping 命令探测"));
            
            if ("FULL".equalsIgnoreCase(scanType)) {
                payloads.add(new PayloadCase("whoami", "root", "Linux 用户身份探测"));
                payloads.add(new PayloadCase("ls -la", "total", "Linux 目录列表探测"));
                payloads.add(new PayloadCase("ps aux", "PID", "Linux 进程列表探测"));
                payloads.add(new PayloadCase("ifconfig", "inet", "Linux 网络配置探测"));
                payloads.add(new PayloadCase("cat /etc/passwd", "root:", "Linux 密码文件探测"));
                payloads.add(new PayloadCase("uname -a", "Linux", "Linux 系统信息探测"));
                payloads.add(new PayloadCase("id", "uid", "Linux 用户ID探测"));
            }
        }
        
        return payloads;
    }
}
