/**
 * 数据报表页面 JavaScript
 * 负责报表数据加载、图表渲染、统计展示等功能
 */

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(30);
    
    loadReportData();
});

/**
 * 时间范围变化时自动调整统计精度
 */
function onReportTimeRangeChange(type) {
    const timeRangeSelect = document.getElementById(`${type}TimeRangeReport`);
    
    const timeRange = timeRangeSelect.value;
    
    const recommendations = {
        '1h': { interval: '5m', display: '5 分钟' },
        '6h': { interval: '10m', display: '10 分钟' },
        '12h': { interval: '30m', display: '30 分钟' },
        '24h': { interval: '30m', display: '30 分钟' },
        '3d': { interval: '1h', display: '1 小时' },
        '7d': { interval: '1h', display: '1 小时' },
        '14d': { interval: '1d', display: '1 天' },
        '30d': { interval: '1d', display: '1 天' }
    };
    
    const recommended = recommendations[timeRange] || { interval: '30m', display: '30 分钟' };
    
    const intervalDisplay = document.getElementById(`${type}IntervalDisplayReport`);
    if (intervalDisplay) {
        intervalDisplay.textContent = `统计精度：${recommended.display}`;
    }
    
    if (type === 'traffic') {
        loadTrafficTrendReport();
    } else if (type === 'attack') {
        loadAttackTrendReport();
    }
}

/**
 * 获取自动推荐的统计精度
 */
function getAutoIntervalReport(timeRange) {
    const recommendations = {
        '1h': '5m',
        '6h': '10m',
        '12h': '30m',
        '24h': '30m',
        '3d': '1h',
        '7d': '1h',
        '14d': '1d',
        '30d': '1d'
    };
    
    return recommendations[timeRange] || '30m';
}

async function loadReportData() {
    await Promise.all([
        loadReportSummary(),
        loadTrafficTrendReport(),
        loadAttackTrendReport(),
        loadAttackTypeChart(),
        loadRiskLevelChart(),
        loadTopAttackers()
    ]);
}

async function loadReportSummary() {
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    try {
        const params = { startDate, endDate };
        const summary = await http.get('/report/summary', params);
        
        document.getElementById('totalTrafficReport').textContent = summary.totalTraffic || 0;
        document.getElementById('totalAttacksReport').textContent = summary.totalAttacks || 0;
        document.getElementById('totalVulnsReport').textContent = summary.totalVulnerabilities || 0;
        document.getElementById('totalDefensesReport').textContent = summary.totalDefenses || 0;
    } catch (error) {
        console.error('加载报表摘要失败:', error);
    }
}

function onQueryReport() {
    loadReportSummary();
}

async function loadTrafficTrendReport() {
    const timeRange = document.getElementById('trafficTimeRangeReport')?.value || '24h';
    const interval = getAutoIntervalReport(timeRange);
    
    try {
        const data = await http.get(`/dashboard/traffic-trend?timeRange=${timeRange}&interval=${interval}&includeAttacks=false&includeDefenses=false`);
        renderTrafficTrendChart(data);
    } catch (error) {
        console.error('加载流量趋势失败:', error);
        renderTrafficTrendChart([]);
    }
}

async function loadAttackTrendReport() {
    const timeRange = document.getElementById('attackTimeRangeReport')?.value || '24h';
    const interval = getAutoIntervalReport(timeRange);
    
    try {
        const data = await http.get(`/dashboard/traffic-trend?timeRange=${timeRange}&interval=${interval}&includeAttacks=true&includeDefenses=false`);
        
        const attackData = data.map(item => ({
            time: item.time,
            count: item.attacks || 0
        }));
        
        renderAttackTrendChart(attackData);
    } catch (error) {
        console.error('加载攻击趋势失败:', error);
        renderAttackTrendChart([]);
    }
}

async function loadAttackTypeChart() {
    try {
        const data = await http.get('/report/attack-type');
        renderAttackTypeChart(data);
    } catch (error) {
        console.error('加载攻击类型分布失败:', error);
        renderAttackTypeChart([]);
    }
}

async function loadRiskLevelChart() {
    try {
        const data = await http.get('/report/risk-level');
        renderRiskLevelChart(data);
    } catch (error) {
        console.error('加载风险等级分布失败:', error);
        renderRiskLevelChart([]);
    }
}

async function loadTopAttackers() {
    try {
        const data = await http.get('/report/top-attackers');
        renderTopAttackers(data);
    } catch (error) {
        console.error('加载攻击源统计失败:', error);
        renderTopAttackers([]);
    }
}

function renderTrafficTrendChart(data) {
    const chartDom = document.getElementById('trafficTrendReportChart');
    const emptyStateDom = document.getElementById('trafficTrendReportEmpty');
    
    if (!chartDom) {
        return;
    }
    
    let dates = [];
    let values = [];
    
    if (Array.isArray(data)) {
        dates = data.map(item => item.time || '');
        values = data.map(item => item.traffic || 0);
    } else if (data.dates && data.values) {
        dates = data.dates;
        values = data.values;
    }
    
    const hasSufficientData = dates.length >= 2 && values.length >= 2;
    
    if (!hasSufficientData) {
        if (emptyStateDom) emptyStateDom.style.display = 'flex';
        chartDom.style.pointerEvents = 'none';
        return;
    }
    
    if (emptyStateDom) emptyStateDom.style.display = 'none';
    chartDom.style.pointerEvents = 'auto';
    
    const chart = chartHelper.init('trafficTrendReportChart');
    if (!chart) {
        return;
    }
    
    const xAxisInterval = calculateXAxisIntervalReport(dates.length);
    
    const option = chartHelper.getOption({
        xAxis: {
            type: 'category',
            data: dates,
            boundaryGap: false,
            axisLabel: {
                rotate: dates.length > 10 ? 45 : 0,
                interval: xAxisInterval
            }
        },
        yAxis: {
            type: 'value'
        },
        series: [{
            data: values,
            type: 'line',
            smooth: true,
            areaStyle: {
                color: 'rgba(24, 144, 255, 0.1)'
            },
            itemStyle: {
                color: '#1890ff'
            }
        }]
    });
    
    chart.setOption(option, { notMerge: true });
}

function calculateXAxisIntervalReport(dataLength) {
    if (dataLength <= 7) {
        return 0;
    } else if (dataLength <= 14) {
        return 1;
    } else if (dataLength <= 30) {
        return Math.floor(dataLength / 10);
    } else if (dataLength <= 100) {
        return Math.floor(dataLength / 15);
    } else {
        return Math.floor(dataLength / 20);
    }
}

function renderAttackTrendChart(data) {
    const chartDom = document.getElementById('attackTrendReportChart');
    const emptyStateDom = document.getElementById('attackTrendReportEmpty');
    
    if (!chartDom) {
        return;
    }
    
    let dates = [];
    let values = [];
    
    if (Array.isArray(data)) {
        dates = data.map(item => item.time || '');
        values = data.map(item => item.count || 0);
    } else if (data.dates && data.values) {
        dates = data.dates;
        values = data.values;
    }
    
    const hasSufficientData = dates.length >= 2 && values.length >= 2;
    
    if (!hasSufficientData) {
        if (emptyStateDom) emptyStateDom.style.display = 'flex';
        chartDom.style.pointerEvents = 'none';
        return;
    }
    
    if (emptyStateDom) emptyStateDom.style.display = 'none';
    chartDom.style.pointerEvents = 'auto';
    
    const chart = chartHelper.init('attackTrendReportChart');
    if (!chart) {
        return;
    }
    
    const xAxisInterval = calculateXAxisIntervalReport(dates.length);
    
    const option = chartHelper.getOption({
        xAxis: {
            type: 'category',
            data: dates,
            boundaryGap: false,
            axisLabel: {
                rotate: dates.length > 10 ? 45 : 0,
                interval: xAxisInterval
            }
        },
        yAxis: {
            type: 'value'
        },
        series: [{
            data: values,
            type: 'line',
            smooth: true,
            areaStyle: {
                color: 'rgba(245, 34, 45, 0.1)'
            },
            itemStyle: {
                color: '#f5222d'
            }
        }]
    });
    
    chart.setOption(option, { notMerge: true });
}

function renderAttackTypeChart(data) {
    const chartDom = document.getElementById('attackTypeReportChart');
    if (!chartDom) {
        return;
    }

    const chart = chartHelper.init('attackTypeReportChart');
    if (!chart) {
        return;
    }
    
    // 处理后端返回的数据格式
    let seriesData = [];
    
    if (Array.isArray(data)) {
        // 后端返回的是 List<Map<String, Object>> 格式
        seriesData = data.map(item => ({
            name: item.name || item.attackType || '未知',
            value: item.value || item.count || 0
        }));
    } else if (data.data) {
        // 兼容旧格式
        seriesData = data.data;
    }
    
    // 处理无数据情况
    if (seriesData.length === 0) {
        seriesData = [{
            name: '无数据',
            value: 1
        }];
    }
    
    const option = {
        tooltip: {
            trigger: 'item'
        },
        legend: {
            orient: 'vertical',
            left: 'left'
        },
        series: [{
            type: 'pie',
            radius: '50%',
            data: seriesData,
            label: {
                show: true,
                formatter: seriesData.length > 0 && seriesData[0].name !== '无数据' ? '{b}: {c}' : '{b}'
            },
            itemStyle: {
                color: seriesData.length > 0 && seriesData[0].name !== '无数据' ? undefined : '#d9d9d9'
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
    
    chart.setOption(option, { notMerge: true });
}

function renderRiskLevelChart(data) {
    const chartDom = document.getElementById('riskLevelReportChart');
    if (!chartDom) {
        return;
    }

    const chart = chartHelper.init('riskLevelReportChart');
    if (!chart) {
        return;
    }
    
    let seriesData = [];
    
    if (Array.isArray(data)) {
        seriesData = data.map(item => ({
            name: item.name || item.riskLevel || '未知',
            value: item.value || item.count || 0
        }));
    } else if (data.data) {
        seriesData = data.data;
    }
    
    if (seriesData.length === 0) {
        seriesData = [{
            name: '无数据',
            value: 1
        }];
    }
    
    const option = {
        tooltip: {
            trigger: 'item'
        },
        legend: {
            orient: 'vertical',
            left: 'left'
        },
        series: [{
            type: 'pie',
            radius: '50%',
            data: seriesData,
            label: {
                show: true,
                formatter: seriesData.length > 0 && seriesData[0].name !== '无数据' ? '{b}: {c}' : '{b}'
            },
            itemStyle: {
                color: seriesData.length > 0 && seriesData[0].name !== '无数据' ? undefined : '#d9d9d9'
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
    
    chart.setOption(option, { notMerge: true });
}

function renderTopAttackers(data) {
    const tbody = document.getElementById('topAttackersBody');
    
    if (!data || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = data.map((item, index) => `
        <tr>
            <td>${index + 1}</td>
            <td>${item.sourceIp}</td>
            <td>${item.attackCount}</td>
            <td>${tableRenderer.renderRiskLevel(item.riskLevel)}</td>
        </tr>
    `).join('');
}
