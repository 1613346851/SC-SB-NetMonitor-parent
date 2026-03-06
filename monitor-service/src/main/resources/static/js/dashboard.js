/**
 * 仪表盘页面 JavaScript
 * 负责仪表盘数据加载、图表渲染、实时告警等功能
 */

// 页面初始化
document.addEventListener('DOMContentLoaded', async function() {
    await loadDashboardStats();
    await loadTrafficTrend();
    await loadAttackTypeDistribution();
    await loadVulnerabilityLevelDistribution();
    await loadRecentAttacks();
    
    setInterval(loadRecentAttacks, 5000);
});

async function loadDashboardStats() {
    try {
        const stats = await http.get('/dashboard/stats');
        
        document.getElementById('totalTraffic').textContent = stats.totalTraffic || 0;
        document.getElementById('totalAttack').textContent = stats.totalAttack || 0;
        document.getElementById('totalVulnerability').textContent = stats.totalVulnerability || 0;
        document.getElementById('totalDefense').textContent = stats.totalDefense || 0;
        
        if (stats.trafficChange !== undefined) {
            const trafficChangeEl = document.getElementById('trafficChange');
            if (stats.trafficChange >= 0) {
                trafficChangeEl.className = 'stat-change up';
                trafficChangeEl.textContent = `↑ ${stats.trafficChange}%`;
            } else {
                trafficChangeEl.className = 'stat-change down';
                trafficChangeEl.textContent = `↓ ${Math.abs(stats.trafficChange)}%`;
            }
        }
    } catch (error) {
        console.error('加载统计数据失败:', error);
    }
}

async function loadTrafficTrend() {
    try {
        const chartData = await http.get('/dashboard/traffic-trend');
        const chart = chartHelper.init('trafficTrendChart');
        
        const option = chartHelper.getOption({
            xAxis: {
                type: 'category',
                data: chartData.dates || [],
                boundaryGap: false
            },
            yAxis: {
                type: 'value'
            },
            series: [{
                data: chartData.values || [],
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
        
        window.addEventListener('resize', () => {
            chart.resize();
        });
    } catch (error) {
        console.error('加载流量趋势失败:', error);
    }
}

async function loadAttackTypeDistribution() {
    try {
        const chartData = await http.get('/dashboard/attack-type');
        const chart = chartHelper.init('attackTypeChart');
        
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
                data: chartData.data || [],
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
        
        window.addEventListener('resize', () => {
            chart.resize();
        });
    } catch (error) {
        console.error('加载攻击类型分布失败:', error);
    }
}

async function loadVulnerabilityLevelDistribution() {
    try {
        const chartData = await http.get('/dashboard/vulnerability-level');
        const chart = chartHelper.init('vulnerabilityLevelChart');
        
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
                data: chartData.data || [],
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
        
        window.addEventListener('resize', () => {
            chart.resize();
        });
    } catch (error) {
        console.error('加载漏洞等级分布失败:', error);
    }
}

async function loadRecentAttacks() {
    try {
        const response = await http.get('/attack/unhandled/high-risk');
        const attacks = response || [];
        
        const tbody = document.getElementById('recentAttacks');
        
        if (!attacks || attacks.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">暂无数据</td></tr>';
            return;
        }
        
        tbody.innerHTML = attacks.map(attack => `
            <tr>
                <td>${dateFormat.format(attack.attackTime)}</td>
                <td>${attack.sourceIp}</td>
                <td>${tableRenderer.renderAttackType(attack.attackType)}</td>
                <td>${tableRenderer.renderRiskLevel(attack.riskLevel)}</td>
                <td>${tableRenderer.renderStatus(attack.handled)}</td>
            </tr>
        `).join('');
    } catch (error) {
        console.error('加载最新告警失败:', error);
    }
}
