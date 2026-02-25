package com.network.target.controller;

import com.network.target.common.ApiResponse;
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
 * 纯命令注入漏洞接口（URL无任何固定命令，完全由参数决定执行内容）
 * 核心：URL路径通用，cmd参数输入什么命令，后端就执行什么命令
 */

/*
    ping（Windows）	http://localhost:9001/target/cmd/execute?cmd=ping 127.0.0.1 -n 2	执行 ping 命令，返回 ping 127.0.0.1 的结果
    tasklist	http://localhost:9001/target/cmd/execute?cmd=tasklist	执行 tasklist，返回 Windows 进程列表
    dir	http://localhost:9001/target/cmd/execute?cmd=dir	执行 dir，返回文件夹列表
    ping（Linux）	http://localhost:9001/target/cmd/execute?cmd=ping 127.0.0.1 -c 2	执行 ping 命令，返回 ping 结果
    ls -l	http://localhost:9001/target/cmd/execute?cmd=ls -l	执行 ls -l，返回 Linux 文件列表
    whoami	http://localhost:9001/target/cmd/execute?cmd=whoami	执行 whoami，返回当前登录用户
*/

@RestController
@RequestMapping("/target/cmd")  // 仅保留模块路径，无固定命令
@Slf4j
public class CmdVulnController {

    /**
     * 通用命令执行接口（路径改为/execute，无任何固定命令）
     * @param cmd 用户输入的任意系统命令（ping、tasklist、dir、ls等）
     */
    @GetMapping("/execute")  // 去掉/ping，改为通用的/execute
    public ApiResponse cmdInject(@RequestParam("cmd") String cmd) {
        try {
            // 1. 纯漏洞逻辑：用户输入的cmd参数直接作为系统命令执行，无任何额外拼接
            String os = System.getProperty("os.name").toLowerCase();
            String finalCmd = os.contains("win")
                    ? "cmd /c " + cmd  // Windows：执行用户输入的任意命令
                    : cmd;             // Linux/Mac：直接执行用户输入的命令

            log.warn("【高危纯命令注入漏洞】执行用户输入的原始命令：{}", finalCmd);

            // 2. 系统编码适配（解决乱码）
            Charset streamCharset = os.contains("win") ? Charset.forName("GBK") : StandardCharsets.UTF_8;
            Process process = Runtime.getRuntime().exec(finalCmd);

            // 读取命令执行结果
            String normalResult = readStream(process.getInputStream(), streamCharset);
            String errorResult = readStream(process.getErrorStream(), streamCharset);

            // 3. 合并结果返回
            StringBuilder totalResult = new StringBuilder();
            if (!normalResult.isEmpty()) {
                totalResult.append("✅ 命令执行成功：\n").append(normalResult);
            }
            if (!errorResult.isEmpty()) {
                totalResult.append("\n❌ 命令执行错误（命令可能非法）：\n").append(errorResult);
            }

            return ApiResponse.success()
                    .message("纯命令注入漏洞触发成功（URL无固定命令）")
                    .data("用户输入的命令", cmd)
                    .data("后端执行的完整命令", finalCmd)
                    .data("命令执行结果", totalResult.toString())
                    .data("漏洞本质", "URL路径通用，用户输入的参数直接作为系统命令执行，无任何固定命令绑定");

        } catch (Exception e) {
            log.error("命令执行异常", e);
            return ApiResponse.error()
                    .message("命令执行失败")
                    .data("用户输入的命令", cmd)
                    .data("异常信息", e.getMessage());
        }
    }

    /**
     * 工具方法：按系统编码读取流（解决乱码）
     */
    private String readStream(InputStream inputStream, Charset charset) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line).append("\n");
        }
        reader.close();
        return result.toString();
    }
}