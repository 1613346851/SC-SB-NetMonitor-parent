/**
 * 网络监测系统 - 图表工具
 * 基于 ECharts 封装，提供统一的图表配置和渲染
 */

(function() {
    const ChartUtil = {
        _instances: new Map(),
        
        init(domId, theme = null) {
            if (typeof echarts === 'undefined') {
                console.error('ECharts is not loaded');
                return null;
            }
            
            const dom = typeof domId === 'string' ? document.getElementById(domId) : domId;
            if (!dom) {
                console.error('Chart container not found:', domId);
                return null;
            }
            
            if (this._instances.has(domId)) {
                this._instances.get(domId).dispose();
            }
            
            const chart = echarts.init(dom, theme);
            this._instances.set(domId, chart);
            
            const resizeHandler = () => chart.resize();
            window.addEventListener('resize', resizeHandler);
            
            chart._resizeHandler = resizeHandler;
            
            return chart;
        },
        
        getChart(domId) {
            return this._instances.get(domId);
        },
        
        dispose(domId) {
            const chart = this._instances.get(domId);
            if (chart) {
                if (chart._resizeHandler) {
                    window.removeEventListener('resize', chart._resizeHandler);
                }
                chart.dispose();
                this._instances.delete(domId);
            }
        },
        
        getBaseOption() {
            return {
                tooltip: {
                    trigger: 'axis',
                    axisPointer: {
                        type: 'shadow'
                    }
                },
                grid: {
                    left: '3%',
                    right: '4%',
                    bottom: '3%',
                    containLabel: true
                },
                textStyle: {
                    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei"'
                }
            };
        },
        
        createLineChart(domId, options = {}) {
            const chart = this.init(domId);
            if (!chart) return null;
            
            const {
                title,
                xAxisData = [],
                series = [],
                legend = true,
                smooth = true,
                areaStyle = true
            } = options;
            
            const seriesConfig = series.map(s => ({
                name: s.name,
                type: 'line',
                smooth,
                data: s.data,
                areaStyle: areaStyle ? {
                    color: s.color ? this.hexToRgba(s.color, 0.1) : 'rgba(79, 70, 229, 0.1)'
                } : undefined,
                itemStyle: {
                    color: s.color || '#4f46e5'
                }
            }));
            
            const option = {
                ...this.getBaseOption(),
                title: title ? { text: title, left: 'center' } : undefined,
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'cross' }
                },
                legend: legend ? {
                    data: series.map(s => s.name),
                    top: 0
                } : undefined,
                xAxis: {
                    type: 'category',
                    data: xAxisData,
                    boundaryGap: false,
                    axisLabel: {
                        rotate: xAxisData.length > 10 ? 45 : 0,
                        interval: this.calculateXAxisInterval(xAxisData.length)
                    }
                },
                yAxis: {
                    type: 'value'
                },
                grid: {
                    left: '3%',
                    right: '4%',
                    bottom: xAxisData.length > 10 ? '15%' : '10%',
                    containLabel: true
                },
                series: seriesConfig
            };
            
            chart.setOption(option);
            return chart;
        },
        
        createPieChart(domId, options = {}) {
            const chart = this.init(domId);
            if (!chart) return null;
            
            const {
                title,
                data = [],
                radius = '50%',
                legendPosition = 'left'
            } = options;
            
            const hasData = data && data.length > 0 && data.some(d => d.value > 0);
            
            const seriesData = hasData ? data : [{ name: '无数据', value: 1 }];
            
            const option = {
                title: title ? { text: title, left: 'center' } : undefined,
                tooltip: {
                    trigger: 'item',
                    formatter: hasData ? '{b}: {c} ({d}%)' : '{b}'
                },
                legend: {
                    orient: 'vertical',
                    left: legendPosition
                },
                series: [{
                    type: 'pie',
                    radius,
                    data: seriesData,
                    label: {
                        show: true,
                        formatter: hasData ? '{b}: {c}' : '{b}'
                    },
                    itemStyle: {
                        color: hasData ? undefined : '#d9d9d9'
                    },
                    emphasis: {
                        itemStyle: {
                            shadowBlur: 10,
                            shadowOffsetX: 0,
                            shadowColor: 'rgba(0, 0, 0, 0.5)'
                        }
                    }
                }]
            };
            
            chart.setOption(option);
            return chart;
        },
        
        createBarChart(domId, options = {}) {
            const chart = this.init(domId);
            if (!chart) return null;
            
            const {
                title,
                xAxisData = [],
                series = [],
                horizontal = false
            } = options;
            
            const seriesConfig = series.map(s => ({
                name: s.name,
                type: 'bar',
                data: s.data,
                itemStyle: {
                    color: s.color || '#4f46e5'
                }
            }));
            
            const option = {
                ...this.getBaseOption(),
                title: title ? { text: title, left: 'center' } : undefined,
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'shadow' }
                },
                legend: {
                    data: series.map(s => s.name),
                    top: 0
                },
                xAxis: {
                    type: horizontal ? 'value' : 'category',
                    data: horizontal ? undefined : xAxisData
                },
                yAxis: {
                    type: horizontal ? 'category' : 'value',
                    data: horizontal ? xAxisData : undefined
                },
                series: seriesConfig
            };
            
            chart.setOption(option);
            return chart;
        },
        
        calculateXAxisInterval(dataLength) {
            if (dataLength <= 7) return 0;
            if (dataLength <= 14) return 1;
            if (dataLength <= 30) return Math.floor(dataLength / 10);
            if (dataLength <= 100) return Math.floor(dataLength / 15);
            return Math.floor(dataLength / 20);
        },
        
        hexToRgba(hex, alpha) {
            const r = parseInt(hex.slice(1, 3), 16);
            const g = parseInt(hex.slice(3, 5), 16);
            const b = parseInt(hex.slice(5, 7), 16);
            return `rgba(${r}, ${g}, ${b}, ${alpha})`;
        },
        
        generateEmptyTimeSeries(timeRange, interval) {
            const data = [];
            const now = new Date();
            
            let totalPoints = 24;
            let intervalMs = 60 * 60 * 1000;
            
            const rangeMatch = timeRange.match(/^(\d+)([hd])$/);
            if (rangeMatch) {
                const amount = parseInt(rangeMatch[1]);
                const unit = rangeMatch[2];
                if (unit === 'h') {
                    totalPoints = amount;
                    intervalMs = 60 * 60 * 1000;
                } else if (unit === 'd') {
                    totalPoints = amount * 24;
                    intervalMs = 60 * 60 * 1000;
                }
            }
            
            for (let i = totalPoints - 1; i >= 0; i--) {
                const time = new Date(now.getTime() - i * intervalMs);
                data.push({
                    time: DateUtil.formatForChart(time),
                    value: 0
                });
            }
            
            return data;
        },
        
        getOption(customOption) {
            const baseOption = this.getBaseOption();
            return { ...baseOption, ...customOption };
        },
        
        formatTimeForChart(date) {
            const pad = n => n.toString().padStart(2, '0');
            return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
        }
    };

    window.ChartUtil = ChartUtil;
    window.chartHelper = ChartUtil;
})();
