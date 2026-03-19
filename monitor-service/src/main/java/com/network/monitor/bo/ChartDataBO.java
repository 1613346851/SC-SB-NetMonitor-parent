package com.network.monitor.bo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 图表数据业务对象
 * 用于封装 ECharts 图表所需的业务数据
 */
@Data
public class ChartDataBO {

    /**
     * 图表类型（line/bar/pie/scatter 等）
     */
    private String chartType;

    /**
     * 图表标题
     */
    private String title;

    /**
     * X 轴数据（类别或时间）
     */
    private List<String> xAxis;

    /**
     * Y 轴数据（数值）
     */
    private List<Number> yAxis;

    /**
     * 数据系列
     */
    private List<Series> series;

    /**
     * 图例数据
     */
    private List<String> legend;

    /**
     * 提示数据
     */
    private Map<String, Object> tooltip;

    /**
     * 图表配置项（JSON 格式）
     */
    private String optionJson;

    /**
     * 数据系列内部类
     */
    @Data
    public static class Series {
        /**
         * 系列名称
         */
        private String name;

        /**
         * 系列类型（line/bar/pie 等）
         */
        private String type;

        /**
         * 数据值
         */
        private List<?> data;

        /**
         * Y 轴索引
         */
        private String yAxisIndex;

        /**
         * 是否平滑曲线（折线图用）
         */
        private Boolean smooth;

        /**
         * 是否填充区域
         */
        private Boolean areaStyle;

        /**
         * 半径（饼图用）
         */
        private String radius;

        /**
         * 中心位置（饼图用，格式："x%,y%"）
         */
        private String center;

        /**
         * 标签位置
         */
        private String labelPosition;
    }

    /**
     * 构建折线图数据
     */
    public static ChartDataBO buildLineChart(String title, List<String> xAxis, List<Number> yAxis) {
        ChartDataBO chartData = new ChartDataBO();
        chartData.setChartType("line");
        chartData.setTitle(title);
        chartData.setXAxis(xAxis);
        chartData.setYAxis(yAxis);
        return chartData;
    }

    /**
     * 构建柱状图数据
     */
    public static ChartDataBO buildBarChart(String title, List<String> xAxis, List<Number> yAxis) {
        ChartDataBO chartData = new ChartDataBO();
        chartData.setChartType("bar");
        chartData.setTitle(title);
        chartData.setXAxis(xAxis);
        chartData.setYAxis(yAxis);
        return chartData;
    }

    /**
     * 构建饼图数据
     */
    public static ChartDataBO buildPieChart(String title, List<Map<String, Object>> data) {
        ChartDataBO chartData = new ChartDataBO();
        chartData.setChartType("pie");
        chartData.setTitle(title);
        
        Series series = new Series();
        series.setName(title);
        series.setType("pie");
        series.setData(data);
        series.setRadius("50%");
        series.setCenter("50%,50%"); // 饼图中心位置，格式为 "x%,y%"
        series.setLabelPosition("inside");
        
        chartData.setSeries(List.of(series));
        return chartData;
    }
}
