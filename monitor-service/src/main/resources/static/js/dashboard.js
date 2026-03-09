/**
 * 仪表盘页面 JavaScript
 * 负责仪表盘数据加载、图表渲染、实时告警等功能
 */

// 页面初始化
document.addEventListener('DOMContentLoaded', async function() {
    // 并行加载所有数据，提升加载速度
    await Promise.all([
        loadDashboardStats(),
        loadTrafficTrend(),
        loadAttackTypeDistribution(),
        loadVulnerabilityLevelDistribution(),
        loadRecentAttacks()
    ]);
    
    // 5 秒后刷新最新告警
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
        // 获取用户选择的参数
        const timeRange = document.getElementById('trafficTimeRange')?.value || '7d';
        const interval = document.getElementById('trafficInterval')?.value || '1h';
        const showAttacks = document.getElementById('showAttacks')?.checked || false;
        const showDefenses = document.getElementById('showDefenses')?.checked || false;
        
        const chartDom = document.getElementById('trafficTrendChart');
        const emptyStateDom = document.getElementById('trafficTrendEmpty');
        
        if (!chartDom) {
            return;
        }
        
        // 调用新接口
        const chartData = await http.get(`/dashboard/traffic-trend?timeRange=${timeRange}&interval=${interval}&includeAttacks=${showAttacks}&includeDefenses=${showDefenses}`);
        
        if (!chartData || chartData.length === 0) {
            // 显示空状态
            if (emptyStateDom) emptyStateDom.style.display = 'flex';
            chartDom.style.pointerEvents = 'none';
            return;
        }
        
        // 隐藏空状态，显示图表
        if (emptyStateDom) emptyStateDom.style.display = 'none';
        chartDom.style.pointerEvents = 'auto';
        
        const chart = chartHelper.init('trafficTrendChart');
        if (!chart) {
            return;
        }
        
        // 准备数据
        const dates = chartData.map(item => item.time);
        const trafficValues = chartData.map(item => item.traffic);
        
        // 构建系列
        const series = [
            {
                name: '流量',
                type: 'line',
                smooth: true,
                data: trafficValues,
                areaStyle: {
                    color: 'rgba(24, 144, 255, 0.1)'
                },
                itemStyle: {
                    color: '#1890ff'
                }
            }
        ];
        
        // 添加攻击趋势
        if (showAttacks && chartData[0]?.attacks !== undefined) {
            const attackValues = chartData.map(item => item.attacks || 0);
            series.push({
                name: '攻击次数',
                type: 'line',
                smooth: true,
                data: attackValues,
                areaStyle: {
                    color: 'rgba(245, 34, 45, 0.1)'
                },
                itemStyle: {
                    color: '#f5222d'
                }
            });
        }
        
        // 添加防御趋势
        if (showDefenses && chartData[0]?.defenses !== undefined) {
            const defenseValues = chartData.map(item => item.defenses || 0);
            series.push({
                name: '防御次数',
                type: 'line',
                smooth: true,
                data: defenseValues,
                areaStyle: {
                    color: 'rgba(82, 196, 26, 0.1)'
                },
                itemStyle: {
                    color: '#52c41a'
                }
            });
        }
        
        const option = chartHelper.getOption({
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'cross'
                }
            },
            legend: {
                data: series.map(s => s.name),
                top: 0
            },
            xAxis: {
                type: 'category',
                data: dates,
                boundaryGap: false,
                axisLabel: {
                    rotate: dates.length > 24 ? 45 : 0
                }
            },
            yAxis: {
                type: 'value'
            },
            series: series
        });
        
        chart.setOption(option);
        
        window.addEventListener('resize', () => {
            chart.resize();
        });
    } catch (error) {
        console.error('加载流量趋势失败:', error);
        // 发生错误时显示空状态
        const emptyStateDom = document.getElementById('trafficTrendEmpty');
        if (emptyStateDom) emptyStateDom.style.display = 'flex';
    }
}

async function loadAttackTypeDistribution() {
    try {
        const chartData = await http.get('/dashboard/attack-type');
        const chartDom = document.getElementById('attackTypeChart');
        
        if (!chartDom) {
            return;
        }

        const chart = chartHelper.init('attackTypeChart');
        if (!chart) {
            return;
        }
        
        // 处理无数据情况
        const seriesData = (chartData && chartData.length > 0) ? chartData : [{
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
                    formatter: chartData && chartData.length > 0 ? '{b}: {c}' : '{b}'
                },
                itemStyle: {
                    color: chartData && chartData.length > 0 ? undefined : '#d9d9d9'
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
        const chartDom = document.getElementById('vulnerabilityLevelChart');
        
        if (!chartDom) {
            return;
        }

        const chart = chartHelper.init('vulnerabilityLevelChart');
        if (!chart) {
            return;
        }
        
        // 处理无数据情况
        const seriesData = (chartData && chartData.length > 0) ? chartData : [{
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
                    formatter: chartData && chartData.length > 0 ? '{b}: {c}' : '{b}'
                },
                itemStyle: {
                    color: chartData && chartData.length > 0 ? undefined : '#d9d9d9'
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
