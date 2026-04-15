/**
 * 攻击事件态势感知页面 JavaScript
 * 展示攻击事件时间线、统计信息和关联攻击记录
 */

let eventTable;
let currentEventId = null;
let attackTypeChart = null;
let riskLevelChart = null;
let eventTrendChart = null;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    initEventTable();
    loadEventStatistics();
    
    initChartsWhenReady();
    
    const urlParams = new URLSearchParams(window.location.search);
    const eventIdFromUrl = urlParams.get('id');
    if (eventIdFromUrl) {
        console.log('从URL参数获取事件ID:', eventIdFromUrl);
        setTimeout(function() {
            loadEventDetail(eventIdFromUrl);
        }, 500);
    }
    
    setInterval(() => {
        loadEventStatistics();
    }, 10000);
});

async function initChartsWhenReady() {
    if (typeof ResourceLoader !== 'undefined') {
        await ResourceLoader.loadEcharts();
    }
    loadCharts();
    loadEventTrendChart();
}

function initEventTable() {
    eventTable = TableUtils.createInstance({
        instanceName: 'eventTable',
        apiUrl: '/event/list',
        pageSize: 10,
        defaultSortField: 'startTime',
        defaultSortOrder: 'desc',
        tableBodyEl: 'eventTableBody',
        paginationEl: 'pagination',
        colspan: 12,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            const statusTag = item.status === 0 
                ? '<span class="tag warning">进行中</span>'
                : '<span class="tag success">已结束</span>';
            
            const duration = formatDuration(item.durationSeconds);
            const confidenceEnd = item.confidenceEnd || 0;
            const peakRpsDisplay = item.peakRps ? `<span class="rps-highlight">${item.peakRps}</span>` : '0';
            
            return `
                <tr onclick="loadEventDetail('${item.eventId}')" style="cursor: pointer;">
                    ${cell.renderCell(item.eventId, { maxLength: 16 })}
                    <td>${dateFormat.format(item.startTime)}</td>
                    <td>${item.sourceIp || '-'}</td>
                    <td>${cell.renderAttackType(item.attackType)}</td>
                    <td>${cell.renderRiskLevel(item.riskLevel)}</td>
                    <td>${duration}</td>
                    <td>${item.totalRequests || 0}</td>
                    <td>${peakRpsDisplay}</td>
                    <td>${item.attackCount || 0}</td>
                    <td><span class="tag ${getConfidenceTagClass(confidenceEnd)}">${confidenceEnd}%</span></td>
                    <td>${statusTag}</td>
                    ${cell.renderActionCell([
                        { text: '详情', type: 'primary', onClick: `event.stopPropagation(); loadEventDetail('${item.eventId}')` }
                    ])}
                </tr>
            `;
        }
    });
    
    window.eventTable = eventTable;
    eventTable.loadData();
}

function formatDuration(seconds) {
    if (!seconds || seconds === 0) return '-';
    
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hours > 0) {
        return `${hours}h ${minutes}m ${secs}s`;
    } else if (minutes > 0) {
        return `${minutes}m ${secs}s`;
    } else {
        return `${secs}s`;
    }
}

function getConfidenceTagClass(confidence) {
    if (confidence >= 90) return 'danger';
    if (confidence >= 70) return 'warning';
    if (confidence >= 30) return 'info';
    return 'success';
}

function searchEvents() {
    const eventId = eventTable.getSearchValue('eventId');
    const sourceIp = eventTable.getSearchValue('sourceIp');
    const attackType = eventTable.getSearchSelectValue('attackType');
    const riskLevel = eventTable.getSearchSelectValue('riskLevel');
    const status = eventTable.getSearchSelectValue('status');
    const defenseAction = eventTable.getSearchSelectValue('defenseAction');
    const defenseSuccess = eventTable.getSearchSelectValue('defenseSuccess');
    const dateRange = eventTable.getDateRangeValue('startDate', 'endDate');
    
    const params = {};
    if (eventId) params.eventId = eventId;
    if (sourceIp) params.sourceIp = sourceIp;
    if (attackType) params.attackType = attackType;
    if (riskLevel) params.riskLevel = riskLevel;
    if (status !== '') params.status = status;
    if (defenseAction) params.defenseAction = defenseAction;
    if (defenseSuccess !== '') params.defenseSuccess = defenseSuccess;
    if (dateRange.startTime) params.startTime = dateRange.startTime;
    if (dateRange.endTime) params.endTime = dateRange.endTime;
    
    eventTable.search(params);
}

function resetSearch() {
    document.getElementById('eventId').value = '';
    document.getElementById('sourceIp').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('riskLevel').value = '';
    document.getElementById('status').value = '';
    document.getElementById('defenseAction').value = '';
    document.getElementById('defenseSuccess').value = '';
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    eventTable.resetSearch();
}

async function loadEventStatistics() {
    try {
        const stats = await http.get('/event/statistics');
        
        document.getElementById('totalEvents').textContent = stats.totalEvents || 0;
        document.getElementById('ongoingEvents').textContent = stats.ongoingEvents || 0;
        document.getElementById('endedEvents').textContent = stats.endedEvents || 0;
        document.getElementById('avgDuration').textContent = formatDuration(stats.avgDuration) || '-';
    } catch (error) {
        console.error('加载事件统计失败:', error);
    }
}

async function loadCharts() {
    try {
        const stats = await http.get('/event/statistics');
        
        renderAttackTypeChart(stats.attackTypeStats || {});
        renderRiskLevelChart(stats.riskLevelStats || {});
    } catch (error) {
        console.error('加载图表数据失败:', error);
    }
}

function renderAttackTypeChart(typeStats) {
    const chartDom = document.getElementById('attackTypeChart');
    if (!chartDom) return;
    
    const chart = chartHelper.init('attackTypeChart');
    if (!chart) return;
    
    const typeNames = {
        'SQL_INJECTION': 'SQL注入',
        'XSS': 'XSS攻击',
        'COMMAND_INJECTION': '命令注入',
        'DDOS': 'DDoS攻击',
        'PATH_TRAVERSAL': '路径遍历',
        'FILE_INCLUSION': '文件包含',
        'BRUTE_FORCE': '暴力破解',
        'SCANNER': '扫描探测',
        'RATE_LIMIT': '频率限制'
    };
    
    const seriesData = Object.entries(typeStats).map(([type, count]) => ({
        name: typeNames[type] || type,
        value: count
    }));
    
    if (seriesData.length === 0) {
        seriesData.push({ name: '暂无数据', value: 1 });
    }
    
    const option = {
        tooltip: {
            trigger: 'item',
            formatter: '{b}: {c} ({d}%)'
        },
        legend: {
            orient: 'vertical',
            left: 'left',
            top: 'center'
        },
        series: [{
            type: 'pie',
            radius: ['40%', '70%'],
            center: ['60%', '50%'],
            avoidLabelOverlap: false,
            itemStyle: {
                borderRadius: 4,
                borderColor: '#fff',
                borderWidth: 2
            },
            label: {
                show: false
            },
            emphasis: {
                label: {
                    show: true,
                    fontSize: 14,
                    fontWeight: 'bold'
                }
            },
            labelLine: {
                show: false
            },
            data: seriesData
        }]
    };
    
    chart.setOption(option, { notMerge: true });
}

function renderRiskLevelChart(riskStats) {
    const chartDom = document.getElementById('riskLevelChart');
    if (!chartDom) return;
    
    const chart = chartHelper.init('riskLevelChart');
    if (!chart) return;
    
    const levelNames = {
        'CRITICAL': '严重',
        'HIGH': '高危',
        'MEDIUM': '中危',
        'LOW': '低危'
    };
    
    const levelColors = {
        'CRITICAL': '#7c3aed',
        'HIGH': '#ef4444',
        'MEDIUM': '#f59e0b',
        'LOW': '#10b981'
    };
    
    const seriesData = Object.entries(riskStats).map(([level, count]) => ({
        name: levelNames[level] || level,
        value: count,
        itemStyle: { color: levelColors[level] }
    }));
    
    if (seriesData.length === 0) {
        seriesData.push({ name: '暂无数据', value: 1, itemStyle: { color: '#d9d9d9' } });
    }
    
    const option = {
        tooltip: {
            trigger: 'item',
            formatter: '{b}: {c} ({d}%)'
        },
        legend: {
            orient: 'vertical',
            left: 'left',
            top: 'center'
        },
        series: [{
            type: 'pie',
            radius: ['40%', '70%'],
            center: ['60%', '50%'],
            avoidLabelOverlap: false,
            itemStyle: {
                borderRadius: 4,
                borderColor: '#fff',
                borderWidth: 2
            },
            label: {
                show: false
            },
            emphasis: {
                label: {
                    show: true,
                    fontSize: 14,
                    fontWeight: 'bold'
                }
            },
            labelLine: {
                show: false
            },
            data: seriesData
        }]
    };
    
    chart.setOption(option, { notMerge: true });
}

function onEventTrendTimeRangeChange() {
    const timeRange = document.getElementById('eventTrendTimeRange').value;
    
    const recommendations = {
        '1h': { interval: '5m', display: '5 分钟' },
        '6h': { interval: '10m', display: '10 分钟' },
        '12h': { interval: '30m', display: '30 分钟' },
        '24h': { interval: '1h', display: '1 小时' },
        '3d': { interval: '1h', display: '1 小时' },
        '7d': { interval: '1h', display: '1 小时' },
        '14d': { interval: '1d', display: '1 天' },
        '30d': { interval: '1d', display: '1 天' }
    };
    
    const recommended = recommendations[timeRange] || { interval: '1h', display: '1 小时' };
    
    const intervalDisplay = document.getElementById('eventTrendIntervalDisplay');
    if (intervalDisplay) {
        intervalDisplay.textContent = `统计精度：${recommended.display}`;
    }
    
    loadEventTrendChart();
}

function getEventTrendAutoInterval(timeRange) {
    const recommendations = {
        '1h': '5m',
        '6h': '10m',
        '12h': '30m',
        '24h': '1h',
        '3d': '1h',
        '7d': '1h',
        '14d': '1d',
        '30d': '1d'
    };
    
    return recommendations[timeRange] || '1h';
}

async function loadEventTrendChart() {
    try {
        const timeRangeSelect = document.getElementById('eventTrendTimeRange');
        const timeRange = timeRangeSelect?.value || '24h';
        const interval = getEventTrendAutoInterval(timeRange);
        
        const chartDom = document.getElementById('eventTrendChart');
        if (!chartDom) return;
        
        const chart = chartHelper.init('eventTrendChart');
        if (!chart) return;
        
        const trendData = await http.get(`/event/trend?timeRange=${timeRange}&interval=${interval}`);
        
        const dates = trendData.map(item => item.time);
        const counts = trendData.map(item => item.count);
        
        const option = {
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'cross'
                }
            },
            xAxis: {
                type: 'category',
                data: dates,
                boundaryGap: false,
                axisLabel: {
                    rotate: dates.length > 10 ? 45 : 0,
                    interval: calculateXAxisInterval(dates.length)
                }
            },
            yAxis: {
                type: 'value',
                minInterval: 1
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: dates.length > 10 ? '15%' : '10%',
                containLabel: true
            },
            series: [{
                name: '攻击事件数',
                type: 'line',
                smooth: true,
                data: counts,
                areaStyle: {
                    color: {
                        type: 'linear',
                        x: 0,
                        y: 0,
                        x2: 0,
                        y2: 1,
                        colorStops: [
                            { offset: 0, color: 'rgba(239, 68, 68, 0.3)' },
                            { offset: 1, color: 'rgba(239, 68, 68, 0.05)' }
                        ]
                    }
                },
                lineStyle: {
                    color: '#ef4444',
                    width: 2
                },
                itemStyle: {
                    color: '#ef4444'
                }
            }]
        };
        
        chart.setOption(option, { notMerge: true });
        
        window.addEventListener('resize', () => {
            chart.resize();
        });
    } catch (error) {
        console.error('加载事件趋势图失败:', error);
    }
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

async function loadEventDetail(eventId) {
    console.log('加载事件详情, eventId:', eventId);
    try {
        const event = await http.get(`/event/eventId/${eventId}`);
        console.log('获取到事件数据:', event);
        
        if (!event) {
            message.error('事件不存在');
            return;
        }
        
        currentEventId = eventId;
        
        document.getElementById('modalEventId').textContent = event.eventId;
        
        const statusHtml = event.status === 0 
            ? '<span class="tag warning">进行中</span>'
            : '<span class="tag success">已结束</span>';
        
        const defenseSuccessHtml = event.defenseSuccess === null 
            ? '-'
            : event.defenseSuccess === 1 
                ? '<span class="tag success">成功</span>'
                : '<span class="tag danger">失败</span>';
        
        const peakRpsValue = event.peakRps || 0;
        const peakRpsHtml = peakRpsValue > 0 
            ? `<span class="rps-highlight">${peakRpsValue}</span>`
            : '0';
        
        const detailContent = document.getElementById('eventDetailContent');
        
        detailContent.innerHTML = `
            <div class="event-detail-grid">
                <div class="detail-section">
                    <h4>基本信息</h4>
                    <div class="detail-item">
                        <label>源 IP:</label>
                        <span>${event.sourceIp || '-'}</span>
                    </div>
                    <div class="detail-item">
                        <label>攻击类型:</label>
                        <span>${tableRenderer.renderAttackType(event.attackType)}</span>
                    </div>
                    <div class="detail-item">
                        <label>风险等级:</label>
                        <span>${tableRenderer.renderRiskLevel(event.riskLevel)}</span>
                    </div>
                    <div class="detail-item">
                        <label>状态:</label>
                        <span>${statusHtml}</span>
                    </div>
                </div>
                
                <div class="detail-section">
                    <h4>时间信息</h4>
                    <div class="detail-item">
                        <label>开始时间:</label>
                        <span>${dateFormat.format(event.startTime)}</span>
                    </div>
                    <div class="detail-item">
                        <label>结束时间:</label>
                        <span>${event.endTime ? dateFormat.format(event.endTime) : '-'}</span>
                    </div>
                    <div class="detail-item">
                        <label>持续时间:</label>
                        <span>${formatDuration(event.durationSeconds)}</span>
                    </div>
                </div>
                
                <div class="detail-section">
                    <h4>攻击统计</h4>
                    <div class="detail-item">
                        <label>总请求数:</label>
                        <span>${event.totalRequests || 0}</span>
                    </div>
                    <div class="detail-item">
                        <label>峰值 RPS:</label>
                        <span>${peakRpsHtml}</span>
                    </div>
                    <div class="detail-item">
                        <label>攻击节点数:</label>
                        <span>${event.attackCount || 0}</span>
                    </div>
                </div>
                
                <div class="detail-section">
                    <h4>置信度变化</h4>
                    <div class="detail-item">
                        <label>初始置信度:</label>
                        <span>${event.confidenceStart ? event.confidenceStart + '%' : '0%'}</span>
                    </div>
                    <div class="detail-item">
                        <label>最终置信度:</label>
                        <span><span class="tag ${getConfidenceTagClass(event.confidenceEnd || 0)}">${event.confidenceEnd || 0}%</span></span>
                    </div>
                </div>
                
                <div class="detail-section">
                    <h4>防御信息</h4>
                    <div class="detail-item">
                        <label>防御动作:</label>
                        <span>${event.defenseAction || '-'}</span>
                    </div>
                    <div class="detail-item">
                        <label>防御结果:</label>
                        <span>${defenseSuccessHtml}</span>
                    </div>
                    <div class="detail-item">
                        <label>过期时间:</label>
                        <span>${event.defenseExpireTime ? dateFormat.format(event.defenseExpireTime) : '-'}</span>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('eventDetailModal').style.display = 'flex';
        
        loadRelatedAttacks(eventId);
        loadEventTimeline(event);
        
    } catch (error) {
        console.error('加载事件详情失败:', error);
        message.error('加载事件详情失败');
    }
}

async function loadRelatedAttacks(eventId) {
    try {
        const attacks = await http.get(`/attack/list?eventId=${eventId}&pageSize=50`);
        const tbody = document.getElementById('relatedAttacksBody');
        
        if (!attacks || !attacks.list || attacks.list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">暂无关联攻击记录</td></tr>';
            return;
        }
        
        tbody.innerHTML = attacks.list.map(attack => `
            <tr>
                <td>${dateFormat.format(attack.createTime)}</td>
                <td>${tableRenderer.renderAttackType(attack.attackType)}</td>
                <td>${tableRenderer.renderRiskLevel(attack.riskLevel)}</td>
                <td>${attack.confidence ? attack.confidence + '%' : '-'}</td>
                <td>${tableRenderer.renderStatus(attack.handled)}</td>
                <td>
                    <a href="/attack?eventId=${eventId}" class="btn btn-link btn-sm">查看</a>
                </td>
            </tr>
        `).join('');
        
    } catch (error) {
        console.error('加载关联攻击记录失败:', error);
    }
}

function loadEventTimeline(event) {
    const timeline = document.getElementById('eventTimeline');
    
    const events = [
        { time: event.startTime, label: '攻击开始', type: 'start' }
    ];
    
    if (event.defenseAction) {
        events.push({ 
            time: event.startTime, 
            label: `防御执行: ${event.defenseAction}`, 
            type: 'defense' 
        });
    }
    
    if (event.endTime) {
        events.push({ time: event.endTime, label: '攻击结束', type: 'end' });
    }
    
    timeline.innerHTML = events.map(e => `
        <div class="timeline-item ${e.type}">
            <div class="timeline-marker"></div>
            <div class="timeline-content">
                <div class="timeline-time">${dateFormat.format(e.time)}</div>
                <div class="timeline-label">${e.label}</div>
            </div>
        </div>
    `).join('');
}

function closeEventDetail() {
    document.getElementById('eventDetailModal').style.display = 'none';
    currentEventId = null;
    
    const url = new URL(window.location);
    url.searchParams.delete('id');
    window.history.replaceState({}, '', url);
}

function viewAttackList() {
    if (currentEventId) {
        window.location.href = `/attack?eventId=${currentEventId}`;
    }
}
