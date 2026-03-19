package com.network.monitor.common.util;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 响应结果封装工具类
 */
public class ResponseUtil {

    /**
     * 设置 JSON 响应头
     */
    public static void setJsonResponseHeaders(HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    /**
     * 写入 JSON 响应
     */
    public static void writeJsonResponse(HttpServletResponse response, String json) throws IOException {
        setJsonResponseHeaders(response);
        PrintWriter writer = response.getWriter();
        writer.write(json);
        writer.flush();
        writer.close();
    }

    /**
     * 设置 CSV 下载响应头
     */
    public static void setCsvDownloadHeaders(HttpServletResponse response, String filename) {
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment;filename=" + encodeFilename(filename));
    }

    /**
     * 文件名编码（兼容不同浏览器）
     */
    private static String encodeFilename(String filename) {
        try {
            return java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            return filename;
        }
    }
}
