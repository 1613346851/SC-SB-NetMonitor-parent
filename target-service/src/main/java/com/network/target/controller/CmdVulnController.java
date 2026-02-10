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
            // 1. 关键1：修正命令拼接格式（Windows下用&&分隔命令，避免被解析为ping参数）
            String os = System.getProperty("os.name").toLowerCase();
            String cmd;
            if (os.contains("win")) {
                // Windows命令：ping完127.0.0.1后，再执行dir（用&&分隔多个命令）
                cmd = "cmd /c ping " + targetIp + " -n 2 && dir";
            } else {
                // Linux/Mac命令：ping完后执行ls -l
                cmd = "ping " + targetIp + " -c 2 && ls -l";
            }
            log.info("【命令注入漏洞接口】执行系统命令：{}", cmd);

            // 2. 关键2：根据操作系统动态指定流编码（Windows用GBK，其他用UTF-8）
            Charset streamCharset = os.contains("win") ? Charset.forName("GBK") : StandardCharsets.UTF_8;

            // 3. 执行命令：同时读取正常流和错误流（避免遗漏异常信息）
            Process process = Runtime.getRuntime().exec(cmd);
            // 读取正常输出流（用对应编码）
            String normalResult = readStream(process.getInputStream(), streamCharset);
            // 读取错误输出流（如命令执行失败的信息）
            String errorResult = readStream(process.getErrorStream(), streamCharset);

            // 4. 合并结果（区分正常和错误信息）
            StringBuilder totalResult = new StringBuilder();
            if (!normalResult.isEmpty()) {
                totalResult.append("正常输出：\n").append(normalResult).append("\n");
            }
            if (!errorResult.isEmpty()) {
                totalResult.append("错误输出：\n").append(errorResult);
            }

            // 5. 返回结果（统一R对象自动转JSON，无乱码）
            return R.ok()
                    .msg("ping+命令执行完成（" + (os.contains("win") ? "Windows" : "Linux/Mac") + "）")
                    .data("executed_cmd", cmd)
                    .data("cmd_result", totalResult.toString())
                    .data("warning", "此接口存在命令注入漏洞，未过滤特殊字符");

        } catch (Exception e) {
            log.error("【命令注入漏洞接口】执行异常", e);
            return R.error()
                    .msg("命令执行失败")
                    .data("error", e.getMessage());
        }
    }

    /**
     * 工具方法：按指定编码读取流数据（核心解决乱码）
     */
    private String readStream(InputStream inputStream, Charset charset) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line).append("\n"); // 保留原始换行，便于查看
        }
        reader.close();
        return result.toString();
    }

}