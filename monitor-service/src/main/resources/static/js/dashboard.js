/**
 * 仪表盘页面 JavaScript
 * 负责仪表盘数据加载、图表渲染、实时告警等功能
 */

let recentAttacksTable;

document.addEventListener('DOMContentLoaded', async function() {
    initRecentAttacksTable();
    
    await Promise.all([
        loadDashboardStats(),
        loadEventStats(),
        loadTrafficTrend(),
        loadAttackTypeDistribution(),
        loadVulnerabilityLevelDistribution(),
        loadRecentEvents()
    ]);
    
    initDataUpdateHandler();
});

function initDataUpdateHandler() {
    if (typeof DataUpdateHandler === 'undefined') {
        console.warn('DataUpdateHandler未加载');
        return;
    }
    
    DataUpdateHandler.connect(
        function() {
            DataUpdateHandler.onDataUpdate(function(message) {
                handleDataUpdate(message);
            });
        },
        function() {
            console.warn('数据更新WebSocket连接断开');
        }
    );
}

function handleDataUpdate(message) {
    if (!message || !message.type) return;
    
    switch (message.type) {
        case 'ALERT_RECORD':
            handleAlertUpdate(message.data);
            break;
        case 'ATTACK_RECORD':
            handleAttackUpdate(message.data);
            break;
        case 'STATS_UPDATE':
            handleStatsUpdate(message.data);
            break;
        case 'EVENT_STATS_UPDATE':
            handleEventStatsUpdate(message.data);
            break;
    }
}

function handleAlertUpdate(alertData) {
    if (!alertData || !recentAttacksTable) return;
    
    const currentData = recentAttacksTable.getCurrentData() || [];
    const existingIndex = currentData.findIndex(item => item.id === alertData.id);
    
    if (existingIndex >= 0) {
        currentData[existingIndex] = alertData;
    } else {
        currentData.unshift(alertData);
        if (currentData.length > 10) {
            currentData.pop();
        }
    }
    
    recentAttacksTable.updateData(currentData);
}

function handleAttackUpdate(attackData) {
    loadDashboardStats();
}

function handleStatsUpdate(statsData) {
    if (statsData.totalTraffic !== undefined) {
        document.getElementById('totalTraffic').textContent = statsData.totalTraffic;
    }
    if (statsData.totalAttack !== undefined) {
        document.getElementById('totalAttack').textContent = statsData.totalAttack;
    }
    if (statsData.totalVulnerability !== undefined) {
        document.getElementById('totalVulnerability').textContent = statsData.totalVulnerability;
    }
    if (statsData.totalDefense !== undefined) {
        document.getElementById('totalDefense').textContent = statsData.totalDefense;
    }
}

function handleEventStatsUpdate(eventStats) {
    if (eventStats.totalEvents !== undefined) {
        const el = document.getElementById('totalEvents');
        if (el) el.textContent = eventStats.totalEvents;
    }
    if (eventStats.activeEvents !== undefined) {
        const el = document.getElementById('activeEvents');
        if (el) el.textContent = eventStats.activeEvents;
    }
    if (eventStats.criticalEvents !== undefined) {
        const el = document.getElementById('criticalEvents');
        if (el) el.textContent = eventStats.criticalEvents;
    }
}

function initRecentAttacksTable() {
    recentAttacksTable = TableUtils.createInstance({
        instanceName: 'recentAttacksTable',
        apiUrl: '/alert/list?pageSize=10&sortField=createTime&sortOrder=desc',
        pageSize: 10,
        defaultSortField: 'createTime',
        defaultSortOrder: 'desc',
        tableBodyEl: 'recentAttacks',
        paginationEl: null,
        colspan: 5,
        enablePagination: false,
        renderRow: function(alert) {
            const levelClass = {
                'CRITICAL': 'tag danger',
                'HIGH': 'tag warning',
                'MEDIUM': 'tag info',
                'LOW': 'tag success'
            }[alert.alertLevel] || 'tag';
            
            const levelText = {
                'CRITICAL': '严重',
                'HIGH': '高危',
                'MEDIUM': '中危',
                'LOW': '低危'
            }[alert.alertLevel] || alert.alertLevel;
            
            const statusText = alert.status === 0 ? '待处理' : (alert.status === 1 ? '已确认' : '已忽略');
            const statusClass = alert.status === 0 ? 'tag warning' : (alert.status === 1 ? 'tag success' : 'tag');
            
            const attackTypeText = {
                'SQL_INJECTION': 'SQL注入',
                'XSS': '跨站脚本',
                'COMMAND_INJECTION': '命令注入',
                'PATH_TRAVERSAL': '路径遍历',
                'FILE_INCLUSION': '文件包含',
                'DDOS': 'DDoS攻击',
                'BRUTE_FORCE': '暴力破解',
                'SCANNER': '扫描器探测'
            }[alert.attackType] || alert.attackType;
            
            return `
                <tr onclick="window.location.href='/alert?alertId=${alert.alertId}'" style="cursor: pointer;">
                    <td>${DateUtil.format(alert.createTime)}</td>
                    <td>${CellRenderer.renderText(alert.sourceIp)}</td>
                    <td>${attackTypeText}</td>
                    <td><span class="${levelClass}">${levelText}</span></td>
                    <td><span class="${statusClass}">${statusText}</span></td>
                </tr>
            `;
        }
    });
    
    window.recentAttacksTable = recentAttacksTable;
    recentAttacksTable.loadData();
}

function onTimeRangeChange() {
    const timeRange = document.getElementById('trafficTimeRange').value;
    
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
    
    const intervalDisplay = document.getElementById('trafficIntervalDisplay');
    if (intervalDisplay) {
        intervalDisplay.textContent = `统计精度：${recommended.display}`;
    }
    
    loadTrafficTrend();
}

function getAutoInterval(timeRange) {
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
        
        if (stats.attackChange !== undefined) {
            const attackChangeEl = document.getElementById('attackChange');
            if (stats.attackChange >= 0) {
                attackChangeEl.className = 'stat-change up';
                attackChangeEl.textContent = `↑ ${stats.attackChange}%`;
            } else {
                attackChangeEl.className = 'stat-change down';
                attackChangeEl.textContent = `↓ ${Math.abs(stats.attackChange)}%`;
            }
        }
        
        if (stats.defenseChange !== undefined) {
            const defenseChangeEl = document.getElementById('defenseChange');
            if (stats.defenseChange >= 0) {
                defenseChangeEl.className = 'stat-change up';
                defenseChangeEl.textContent = `↑ ${stats.defenseChange}%`;
            } else {
                defenseChangeEl.className = 'stat-change down';
                defenseChangeEl.textContent = `↓ ${Math.abs(stats.defenseChange)}%`;
            }
        }
        
        if (stats.vulnerabilityChange !== undefined) {
            const vulnerabilityChangeEl = document.getElementById('vulnerabilityChange');
            vulnerabilityChangeEl.className = 'stat-change';
            vulnerabilityChangeEl.textContent = `${stats.vulnerabilityChange} 个`;
        }
    } catch (error) {
        console.error('加载统计数据失败:', error);
    }
}

async function loadEventStats() {
    try {
        const stats = await http.get('/event/statistics');
        
        const ongoingEventsEl = document.getElementById('ongoingEvents');
        if (ongoingEventsEl) {
            ongoingEventsEl.textContent = stats.ongoingEvents || 0;
        }
        
        const totalEventsEl = document.getElementById('totalEvents');
        if (totalEventsEl) {
            totalEventsEl.textContent = stats.totalEvents || 0;
        }
    } catch (error) {
        console.error('加载事件统计失败:', error);
    }
}

async function loadTrafficTrend() {
    try {
        const timeRangeSelect = document.getElementById('trafficTimeRange');
        
        const timeRange = timeRangeSelect?.value || '24h';
        const interval = getAutoInterval(timeRange);
        
        const chartDom = document.getElementById('trafficTrendChart');
        const emptyStateDom = document.getElementById('trafficTrendEmpty');
        const emptyTextDom = document.getElementById('trafficTrendEmptyText');
        
        if (!chartDom) {
            return;
        }
        
        const chartData = await http.get(`/dashboard/traffic-trend?timeRange=${timeRange}&interval=${interval}&includeAttacks=true&includeDefenses=true`);
        
        if (emptyStateDom) emptyStateDom.style.display = 'none';
        chartDom.style.pointerEvents = 'auto';
        
        const chart = chartHelper.init('trafficTrendChart');
        if (!chart) {
            return;
        }
        
        let finalData = chartData;
        if (!chartData || chartData.length === 0) {
            finalData = generateEmptyTimeSeries(timeRange, interval);
        }
        
        const dates = finalData.map(item => item.time);
        
        const series = [];
        
        const trafficValues = finalData.map(item => item.traffic || 0);
        series.push({
            name: '流量',
            type: 'line',
            smooth: true,
            data: trafficValues,
            areaStyle: {
                color: 'rgba(79, 70, 229, 0.1)'
            },
            itemStyle: {
                color: '#4f46e5'
            }
        });
        
        const attackValues = finalData.map(item => item.attacks || 0);
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
        
        const defenseValues = finalData.map(item => item.defenses || 0);
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
        
        const xAxisInterval = calculateXAxisInterval(dates.length);
        
        const option = chartHelper.getOption({
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'cross'
                }
            },
            legend: {
                data: series.map(s => s.name),
                top: 0,
                selected: {
                    '流量': true,
                    '攻击次数': true,
                    '防御次数': true
                }
            },
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
            grid: {
                left: '3%',
                right: '4%',
                bottom: dates.length > 10 ? '15%' : '10%',
                containLabel: true
            },
            series: series
        });
        
        chart.setOption(option, { notMerge: true });
        
        window.addEventListener('resize', () => {
            chart.resize();
        });
    } catch (error) {
        console.error('加载流量趋势失败:', error);
        const emptyStateDom = document.getElementById('trafficTrendEmpty');
        const emptyTextDom = document.getElementById('trafficTrendEmptyText');
        if (emptyStateDom) {
            emptyStateDom.style.display = 'flex';
            if (emptyTextDom) {
                emptyTextDom.textContent = '数据获取异常';
            }
        }
    }
}

function generateEmptyTimeSeries(timeRange, interval) {
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
        const timeStr = formatTimeForChart(time);
        data.push({
            time: timeStr,
            traffic: 0,
            attacks: 0,
            defenses: 0
        });
    }
    
    return data;
}

function formatTimeForChart(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}`;
}

function calculateXAxisInterval(dataLength) {
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

async function loadRecentEvents() {
    try {
        const events = await http.get('/event/recent?limit=5');
        
        const tbody = document.getElementById('recentEvents');
        if (!tbody) return;
        
        if (!events || events.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">暂无数据</td></tr>';
            return;
        }
        
        tbody.innerHTML = events.map(event => {
            const statusTag = event.status === 0 
                ? '<span class="tag warning">进行中</span>'
                : '<span class="tag success">已结束</span>';
            
            return `
                <tr onclick="window.location.href='/event?id=${event.eventId}'" style="cursor: pointer;">
                    <td><span class="event-id" title="${event.eventId}">${event.eventId.substring(0, 12)}...</span></td>
                    <td>${DateUtil.format(event.startTime)}</td>
                    <td>${CellRenderer.renderText(event.sourceIp)}</td>
                    <td>${TableRenderer.renderAttackType(event.attackType)}</td>
                    <td>${statusTag}</td>
                </tr>
            `;
        }).join('');
    } catch (error) {
        console.error('加载最近事件失败:', error);
    }
}
