package com.network.target.controller;

import com.network.target.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 命令注入漏洞接口（预设漏洞）
 * 接口路径：/target/cmd/**，模拟“系统信息查询”场景
 */
@RestController
@RequestMapping("/target/cmd")
@Slf4j
public class CmdVulnController {

    /**
     * 模拟“ping测试”接口（存在命令注入漏洞）
     * 漏洞点：直接拼接用户输入的IP到系统命令，未做过滤
     * 测试攻击请求：http://localhost:9001/target/cmd/ping?ip=127.0.0.1;ls -l
     */
    @GetMapping("/ping")
    public R pingTest(@RequestParam("ip") String targetIp) {
        try {
            // 1. 关键：保留漏洞点——用户输入原样拼接（含;等特殊字符）
            String os = System.getProperty("os.name").toLowerCase();
            String cmd;
            if (os.contains("win")) {
                // Windows：必须用cmd /c 才能解析;分隔的多命令（如 ping 127.0.0.1 -n 2;dir）
                cmd = "cmd /c ping " + targetIp + " -n 2";
            } else {
                // Linux/Mac：直接拼接命令（如 ping 127.0.0.1 -c 2;ls -l）
                cmd = "ping " + targetIp + " -c 2";
            }
            log.info("【命令注入漏洞接口】执行用户拼接命令：{}", cmd);

            // 2. 关键：按操作系统动态指定流编码（解决乱码核心）
            Charset streamCharset = os.contains("win")
                    ? Charset.forName("GBK")  // Windows命令输出默认GBK
                    : StandardCharsets.UTF_8;// Linux/Mac默认UTF-8

            // 3. 执行命令：同时读取正常流和错误流（避免遗漏攻击结果）
            Process process = Runtime.getRuntime().exec(cmd);
            String normalResult = readStream(process.getInputStream(), streamCharset);
            String errorResult = readStream(process.getErrorStream(), streamCharset);

            // 4. 合并结果（展示ping和注入命令的执行结果）
            StringBuilder totalResult = new StringBuilder();
            if (!normalResult.isEmpty()) {
                totalResult.append("=== 正常输出（含注入命令结果） ===\n").append(normalResult);
            }
            if (!errorResult.isEmpty()) {
                totalResult.append("\n=== 错误输出 ===\n").append(errorResult);
            }

            // 5. 返回结果（统一R对象确保响应编码UTF-8，无乱码）
            return R.ok()
                    .msg("命令注入执行完成（" + (os.contains("win") ? "Windows" : "Linux/Mac") + "）")
                    .data("user_input_ip", targetIp)  // 显示用户原始输入（含;）
                    .data("executed_cmd", cmd)        // 显示最终执行的命令
                    .data("cmd_result", totalResult.toString())
                    .data("warning", "漏洞生效！用户输入的;已触发多命令执行，模拟命令注入成功");

        } catch (Exception e) {
            log.error("【命令注入漏洞接口】执行异常", e);
            return R.error()
                    .msg("命令执行失败")
                    .data("error", e.getMessage())
                    .data("user_input_ip", targetIp);
        }
    }

    /**
     * 工具方法：按指定编码读取流（不改变原始数据，仅解决编码转换）
     */
    private String readStream(InputStream inputStream, Charset charset) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line).append("\n"); // 保留命令输出的原始格式
        }
        reader.close();
        return result.toString();
    }

}