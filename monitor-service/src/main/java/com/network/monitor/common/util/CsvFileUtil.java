package com.network.monitor.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * CSV 文件处理工具类
 * 用于生成和导出 CSV 格式数据
 */
@Slf4j
public class CsvFileUtil {

    /**
     * CSV 分隔符
     */
    private static final String COMMA = ",";

    /**
     * CSV 换行符
     */
    private static final String NEW_LINE = "\n";

    /**
     * CSV 引用符号
     */
    private static final String QUOTE = "\"";

    /**
     * 将数据写入 CSV
     *
     * @param writer     写入器
     * @param headers    表头
     * @param data       数据列表
     * @param ignoreNull 是否忽略 null 值
     */
    public static void writeCsv(Writer writer, List<String> headers, List<Map<String, Object>> data, boolean ignoreNull) {
        try {
            writer.write('\uFEFF');

            writeHeader(writer, headers);

            for (Map<String, Object> row : data) {
                writeRow(writer, headers, row, ignoreNull);
            }

            writer.flush();
            log.info("写入 CSV 数据成功，行数：{}", data.size());
        } catch (Exception e) {
            log.error("写入 CSV 数据失败：", e);
            throw new RuntimeException("写入 CSV 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 写入表头
     */
    private static void writeHeader(Writer writer, List<String> headers) throws Exception {
        for (int i = 0; i < headers.size(); i++) {
            writer.append(escape(headers.get(i)));
            if (i < headers.size() - 1) {
                writer.append(COMMA);
            }
        }
        writer.append(NEW_LINE);
    }

    /**
     * 写入数据行
     */
    private static void writeRow(Writer writer, List<String> headers, Map<String, Object> row, boolean ignoreNull) throws Exception {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            Object value = row.get(header);

            if (value == null) {
                if (ignoreNull) {
                    writer.append("");
                } else {
                    writer.append(escape(""));
                }
            } else {
                writer.append(escape(value.toString()));
            }

            if (i < headers.size() - 1) {
                writer.append(COMMA);
            }
        }
        writer.append(NEW_LINE);
    }

    /**
     * 转义 CSV 特殊字符
     *
     * @param content 内容
     * @return 转义后的内容
     */
    private static String escape(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // 如果内容包含逗号、换行符或引号，需要用引号包裹
        if (content.contains(COMMA) || content.contains(NEW_LINE) || content.contains(QUOTE)) {
            return QUOTE + content.replace(QUOTE, QUOTE + QUOTE) + QUOTE;
        }

        return content;
    }

    /**
     * 生成 CSV 文件名
     *
     * @param prefix 文件名前缀
     * @return CSV 文件名
     */
    public static String generateCsvFileName(String prefix) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + "_" + timestamp + ".csv";
    }
}
