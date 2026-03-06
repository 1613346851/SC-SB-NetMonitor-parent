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
        
        const summary = await http.get('/report/summary', params);
        document.getElementById('totalTrafficReport').textContent = summary.totalTraffic || 0;
        document.getElementById('totalAttacksReport').textContent = summary.totalAttacks || 0;
        document.getElementById('totalVulnsReport').textContent = summary.totalVulnerabilities || 0;
        document.getElementById('totalDefensesReport').textContent = summary.totalDefenses || 0;
        
        const trafficTrend = await http.get('/report/traffic-trend', params);
        renderTrafficTrendChart(trafficTrend);
        
        const attackTrend = await http.get('/report/attack-trend', params);
        renderAttackTrendChart(attackTrend);
        
        const attackType = await http.get('/report/attack-type', params);
        renderAttackTypeChart(attackType);
        
        const riskLevel = await http.get('/report/risk-level', params);
        renderRiskLevelChart(riskLevel);
        
        const topAttackers = await http.get('/report/top-attackers', params);
        renderTopAttackers(topAttackers);
    } catch (error) {
        console.error('加载报表数据失败:', error);
    }
}

function renderTrafficTrendChart(data) {
    const chart = chartHelper.init('trafficTrendReportChart');
    
    const option = chartHelper.getOption({
        xAxis: {
            type: 'category',
            data: data.dates || [],
            boundaryGap: false
        },
        yAxis: {
            type: 'value'
        },
        series: [{
            data: data.values || [],
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
    const chart = chartHelper.init('attackTrendReportChart');
    
    const option = chartHelper.getOption({
        xAxis: {
            type: 'category',
            data: data.dates || [],
            boundaryGap: false
        },
        yAxis: {
            type: 'value'
        },
        series: [{
            data: data.values || [],
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
    const chart = chartHelper.init('attackTypeReportChart');
    
    const option = chartHelper.getOption({
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
            data: data.data || [],
            emphasis: {
                itemStyle: {
                    shadowBlur: 10,
                    shadowOffsetX: 0,
                    shadowColor: 'rgba(0, 0, 0, 0.5)'
                }
            }
        }]
    });
    
    chart.setOption(option);
}

function renderRiskLevelChart(data) {
    const chart = chartHelper.init('riskLevelReportChart');
    
    const option = chartHelper.getOption({
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
            data: data.data || [],
            emphasis: {
                itemStyle: {
                    shadowBlur: 10,
                    shadowOffsetX: 0,
                    shadowColor: 'rgba(0, 0, 0, 0.5)'
                }
            }
        }]
    });
    
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
