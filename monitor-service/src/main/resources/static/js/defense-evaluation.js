/**
 * 防御效果评估页面 JavaScript
 */

let defenseChart = null;
let successRateChart = null;
let defenseLogTable;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = DateUtil.daysAgo(7);
    
    loadDefenseStatistics();
    loadDefenseTrend();
    loadSuccessRateByType();
    initDefenseLogTable();
    
    setInterval(() => {
        loadDefenseStatistics();
    }, 30000);
});

function initDefenseLogTable() {
    defenseLogTable = TableUtils.createInstance({
        instanceName: 'defenseLogTable',
        apiUrl: '/defense/list',
        pageSize: 10,
        defaultSortField: 'createTime',
        defaultSortOrder: 'desc',
        tableBodyEl: 'recentDefenseBody',
        paginationEl: 'defensePagination',
        colspan: 7,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
                    <td>${DateUtil.format(item.createTime)}</td>
                    <td>${renderDefenseType(item.defenseType)}</td>
                    ${cell.renderCell(item.defenseTarget, { maxLength: 20 })}
                    <td>${renderDefenseAction(item.defenseAction)}</td>
                    <td>${item.executeStatus === 1 ? '<span class="tag success">成功</span>' : '<span class="tag danger">失败</span>'}</td>
                    <td>${item.eventId ? `<a href="/event?id=${item.eventId}" class="event-link">${item.eventId.substring(0, 8)}...</a>` : '-'}</td>
                    ${cell.renderActionCell([
                        { text: '详情', type: 'primary', onClick: `showDefenseDetail(${item.id})` }
                    ])}
                </tr>
            `;
        }
    });
    
    window.defenseLogTable = defenseLogTable;
    defenseLogTable.loadData();
}

function showDefenseDetail(id) {
    http.get('/defense/' + id)
        .then(result => {
            if (result) {
                const detailContent = document.getElementById('defenseDetailContent');
                detailContent.innerHTML = `
                    <div class="detail-section">
                        <h4>基本信息</h4>
                        <div class="detail-item">
                            <label>防御类型:</label>
                            <span>${renderDefenseType(result.defenseType)}</span>
                        </div>
                        <div class="detail-item">
                            <label>防御动作:</label>
                            <span>${renderDefenseAction(result.defenseAction)}</span>
                        </div>
                        <div class="detail-item">
                            <label>防御目标:</label>
                            <span>${result.defenseTarget || '-'}</span>
                        </div>
                        <div class="detail-item">
                            <label>执行结果:</label>
                            <span>${result.executeStatus === 1 ? '<span class="tag success">成功</span>' : '<span class="tag danger">失败</span>'}</span>
                        </div>
                    </div>
                    <div class="detail-section">
                        <h4>详细信息</h4>
                        <div class="detail-item">
                            <label>关联事件:</label>
                            <span>${result.eventId || '-'}</span>
                        </div>
                        <div class="detail-item">
                            <label>防御原因:</label>
                            <span>${result.defenseReason || '-'}</span>
                        </div>
                        <div class="detail-item">
                            <label>过期时间:</label>
                            <span>${result.expireTime || '-'}</span>
                        </div>
                        <div class="detail-item">
                            <label>执行时间:</label>
                            <span>${result.createTime || '-'}</span>
                        </div>
                    </div>
                `;
                
                document.getElementById('defenseDetailModal').style.display = 'flex';
            }
        })
        .catch(error => {
            message.error('获取防御详情失败: ' + (error.message || '未知错误'));
        });
}

function closeDefenseDetailModal() {
    document.getElementById('defenseDetailModal').style.display = 'none';
}

function renderDefenseType(type) {
    const typeMap = {
        'BLOCK_IP': '<span class="tag danger">IP封禁</span>',
        'RATE_LIMIT': '<span class="tag warning">限流</span>',
        'BLOCK_REQUEST': '<span class="tag info">请求拦截</span>',
        'REDIRECT': '<span class="tag info">重定向</span>',
        'CAPTCHA': '<span class="tag info">验证码</span>'
    };
    return typeMap[type] || `<span class="tag">${type || '-'}</span>`;
}

function renderDefenseAction(action) {
    const actionMap = {
        'BLOCK': '阻断',
        'LIMIT': '限速',
        'REDIRECT': '重定向',
        'CHALLENGE': '挑战'
    };
    return actionMap[action] || action || '-';
}

async function loadDefenseStatistics() {
    try {
        const startDate = document.getElementById('startDate').value;
        const endDate = document.getElementById('endDate').value;
        
        const stats = await http.get(`/defense/statistics?startDate=${startDate}&endDate=${endDate}`);
        
        document.getElementById('totalDefenses').textContent = stats.totalDefenses || 0;
        document.getElementById('successDefenses').textContent = stats.successDefenses || 0;
        document.getElementById('failedDefenses').textContent = stats.failedDefenses || 0;
        
        const successRate = stats.totalDefenses > 0 
            ? ((stats.successDefenses / stats.totalDefenses) * 100).toFixed(1)
            : 0;
        document.getElementById('successRate').textContent = successRate + '%';
        
        document.getElementById('blockCount').textContent = stats.blockCount || 0;
        document.getElementById('rateLimitCount').textContent = stats.rateLimitCount || 0;
        document.getElementById('redirectCount').textContent = stats.redirectCount || 0;
        
    } catch (error) {
        console.error('加载防御统计失败:', error);
    }
}

async function loadDefenseTrend() {
    try {
        const startDate = document.getElementById('startDate').value;
        const endDate = document.getElementById('endDate').value;
        
        const data = await http.get(`/defense/trend?startDate=${startDate}&endDate=${endDate}`);
        
        const chartDom = document.getElementById('defenseTrendChart');
        defenseChart = echarts.init(chartDom);
        
        const dates = data.map(item => item.date);
        const successData = data.map(item => item.success || 0);
        const failData = data.map(item => item.fail || 0);
        
        const option = {
            tooltip: {
                trigger: 'axis',
                axisPointer: { type: 'shadow' }
            },
            legend: {
                data: ['成功', '失败'],
                bottom: 0
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '15%',
                top: '10%',
                containLabel: true
            },
            xAxis: {
                type: 'category',
                data: dates,
                axisLabel: { rotate: 45 }
            },
            yAxis: { type: 'value' },
            series: [
                {
                    name: '成功',
                    type: 'bar',
                    stack: 'total',
                    data: successData,
                    itemStyle: { color: '#10b981' }
                },
                {
                    name: '失败',
                    type: 'bar',
                    stack: 'total',
                    data: failData,
                    itemStyle: { color: '#ef4444' }
                }
            ]
        };
        
        defenseChart.setOption(option);
        
        window.addEventListener('resize', () => {
            defenseChart && defenseChart.resize();
        });
        
    } catch (error) {
        console.error('加载防御趋势失败:', error);
    }
}

async function loadSuccessRateByType() {
    try {
        const startDate = document.getElementById('startDate').value;
        const endDate = document.getElementById('endDate').value;
        
        const data = await http.get(`/defense/success-rate-by-type?startDate=${startDate}&endDate=${endDate}`);
        
        const chartDom = document.getElementById('successRateChart');
        successRateChart = echarts.init(chartDom);
        
        const types = data.map(item => item.type);
        const rates = data.map(item => item.rate || 0);
        
        const option = {
            tooltip: {
                trigger: 'axis',
                formatter: '{b}: {c}%'
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '3%',
                top: '10%',
                containLabel: true
            },
            xAxis: {
                type: 'category',
                data: types,
                axisLabel: { interval: 0, rotate: 30 }
            },
            yAxis: {
                type: 'value',
                max: 100,
                axisLabel: { formatter: '{value}%' }
            },
            series: [
                {
                    name: '成功率',
                    type: 'bar',
                    data: rates,
                    itemStyle: {
                        color: function(params) {
                            const value = params.value;
                            if (value >= 90) return '#10b981';
                            if (value >= 70) return '#f59e0b';
                            return '#ef4444';
                        }
                    },
                    label: {
                        show: true,
                        position: 'top',
                        formatter: '{c}%'
                    }
                }
            ]
        };
        
        successRateChart.setOption(option);
        
        window.addEventListener('resize', () => {
            successRateChart && successRateChart.resize();
        });
        
    } catch (error) {
        console.error('加载成功率分析失败:', error);
    }
}

function refreshData() {
    loadDefenseStatistics();
    loadDefenseTrend();
    loadSuccessRateByType();
    defenseLogTable.refresh();
}

function exportReport() {
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    window.location.href = `/defense/export-report?startDate=${startDate}&endDate=${endDate}`;
}
