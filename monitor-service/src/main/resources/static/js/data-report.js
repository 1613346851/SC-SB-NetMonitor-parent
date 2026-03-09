/**
 * 数据报表页面 JavaScript
 * 负责报表数据加载、图表渲染、统计展示等功能
 */

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(30);
    
    loadReportData();
});

async function loadReportData() {
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    try {
        const params = { startDate, endDate };
        
        // 并行加载所有数据，提升加载速度
        const [summary, trafficTrend, attackTrend, attackType, riskLevel, topAttackers] = await Promise.all([
            http.get('/report/summary', params),
            http.get('/report/traffic-trend', params),
            http.get('/report/attack-trend', params),
            http.get('/report/attack-type', params),
            http.get('/report/risk-level', params),
            http.get('/report/top-attackers', params)
        ]);
        
        document.getElementById('totalTrafficReport').textContent = summary.totalTraffic || 0;
        document.getElementById('totalAttacksReport').textContent = summary.totalAttacks || 0;
        document.getElementById('totalVulnsReport').textContent = summary.totalVulnerabilities || 0;
        document.getElementById('totalDefensesReport').textContent = summary.totalDefenses || 0;
        
        renderTrafficTrendChart(trafficTrend);
        renderAttackTrendChart(attackTrend);
        renderAttackTypeChart(attackType);
        renderRiskLevelChart(riskLevel);
        renderTopAttackers(topAttackers);
    } catch (error) {
        console.error('加载报表数据失败:', error);
    }
}

function renderTrafficTrendChart(data) {
    const chartDom = document.getElementById('trafficTrendReportChart');
    const emptyStateDom = document.getElementById('trafficTrendReportEmpty');
    
    if (!chartDom) {
        return;
    }
    
    // 检测数据是否充分
    const dates = data.dates || [];
    const values = data.values || [];
    const hasSufficientData = dates.length >= 2 && values.length >= 2;
    
    if (!hasSufficientData) {
        // 显示空状态，隐藏图表
        if (emptyStateDom) emptyStateDom.style.display = 'flex';
        chartDom.style.pointerEvents = 'none';
        return;
    }
    
    // 隐藏空状态，显示图表
    if (emptyStateDom) emptyStateDom.style.display = 'none';
    chartDom.style.pointerEvents = 'auto';
    
    const chart = chartHelper.init('trafficTrendReportChart');
    if (!chart) {
        return;
    }
    
    const option = chartHelper.getOption({
        xAxis: {
            type: 'category',
            data: dates,
            boundaryGap: false
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
    
    chart.setOption(option);
}

function renderAttackTrendChart(data) {
    const chartDom = document.getElementById('attackTrendReportChart');
    const emptyStateDom = document.getElementById('attackTrendReportEmpty');
    
    if (!chartDom) {
        return;
    }
    
    // 检测数据是否充分
    const dates = data.dates || [];
    const values = data.values || [];
    const hasSufficientData = dates.length >= 2 && values.length >= 2;
    
    if (!hasSufficientData) {
        // 显示空状态，隐藏图表
        if (emptyStateDom) emptyStateDom.style.display = 'flex';
        chartDom.style.pointerEvents = 'none';
        return;
    }
    
    // 隐藏空状态，显示图表
    if (emptyStateDom) emptyStateDom.style.display = 'none';
    chartDom.style.pointerEvents = 'auto';
    
    const chart = chartHelper.init('attackTrendReportChart');
    if (!chart) {
        return;
    }
    
    const option = chartHelper.getOption({
        xAxis: {
            type: 'category',
            data: dates,
            boundaryGap: false
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
    
    chart.setOption(option);
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
    
    // 处理无数据情况
    const seriesData = (data.data && data.data.length > 0) ? data.data : [{
        name: '无数据',
        value: 1
    }];
    
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
                formatter: data.data && data.data.length > 0 ? '{b}: {c}' : '{b}'
            },
            itemStyle: {
                color: data.data && data.data.length > 0 ? undefined : '#d9d9d9'
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
    
    // 处理无数据情况
    const seriesData = (data.data && data.data.length > 0) ? data.data : [{
        name: '无数据',
        value: 1
    }];
    
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
                formatter: data.data && data.data.length > 0 ? '{b}: {c}' : '{b}'
            },
            itemStyle: {
                color: data.data && data.data.length > 0 ? undefined : '#d9d9d9'
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
