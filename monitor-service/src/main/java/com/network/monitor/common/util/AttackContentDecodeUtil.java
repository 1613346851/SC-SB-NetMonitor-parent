package com.network.monitor.common.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 攻击内容解码工具类
 */
public class AttackContentDecodeUtil {

    /**
     * URL 编码正则匹配
     */
    private static final Pattern URL_ENCODE_PATTERN = Pattern.compile("%[0-9A-Fa-f]{2}");

    /**
     * Unicode 编码正则匹配
     */
    private static final Pattern UNICODE_ENCODE_PATTERN = Pattern.compile("\\\\u[0-9A-Fa-f]{4}");

    /**
     * HTML 实体编码正则匹配
     */
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&#?[0-9A-Za-z]+;");

    /**
     * 解码攻击内容（URL 解码、Unicode 解码、HTML 实体解码）
     *
     * @param content 原始攻击内容
     * @return 解码后的内容
     */
    public static String decode(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String decoded = content;
        int maxIterations = 5; // 防止无限嵌套解码

        for (int i = 0; i < maxIterations; i++) {
            String previous = decoded;

            // URL 解码
            decoded = urlDecode(decoded);

            // Unicode 解码
            decoded = unicodeDecode(decoded);

            // HTML 实体解码
            decoded = htmlEntityDecode(decoded);

            // 如果没有变化，退出循环
            if (previous.equals(decoded)) {
                break;
            }
        }

        return decoded;
    }

    /**
     * URL 解码
     */
    private static String urlDecode(String input) {
        try {
            if (URL_ENCODE_PATTERN.matcher(input).find()) {
                return URLDecoder.decode(input, StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            // 忽略解码异常
        }
        return input;
    }

    /**
     * Unicode 解码
     */
    private static String unicodeDecode(String input) {
        Matcher matcher = UNICODE_ENCODE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group().substring(2);
            char ch = (char) Integer.parseInt(hex, 16);
            matcher.appendReplacement(sb, String.valueOf(ch));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * HTML 实体解码（简化版）
     */
    private static String htmlEntityDecode(String input) {
        return input.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    /**
     * 过滤空白字符和 SQL 注释
     */
    public static String filterWhitespaceAndComments(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 移除所有空白字符（空格、制表符、换行等）
        String filtered = input.replaceAll("\\s+", "");

        // 移除 SQL 单行注释--
        filtered = filtered.replaceAll("--[^\\n]*", "");

        // 移除 SQL 多行注释/**/
        filtered = filtered.replaceAll("/\\*.*?\\*/", "");

        // 移除#注释
        filtered = filtered.replaceAll("#[^\\n]*", "");

        return filtered;
    }

    /**
     * 标准化攻击内容（解码 + 过滤）
     */
    public static String normalize(String content) {
        String decoded = decode(content);
        return filterWhitespaceAndComments(decoded);
    }
}
