/**
 * 防御效果评估页面 JavaScript
 * 展示防御效果统计、成功率分析和趋势图
 */

let defenseChart = null;
let successRateChart = null;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    loadDefenseStatistics();
    loadDefenseTrend();
    loadSuccessRateByType();
    loadRecentDefenseLogs();
    
    setInterval(() => {
        loadDefenseStatistics();
    }, 30000);
});

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
                axisPointer: {
                    type: 'shadow'
                }
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
                axisLabel: {
                    rotate: 45
                }
            },
            yAxis: {
                type: 'value'
            },
            series: [
                {
                    name: '成功',
                    type: 'bar',
                    stack: 'total',
                    data: successData,
                    itemStyle: {
                        color: '#10b981'
                    }
                },
                {
                    name: '失败',
                    type: 'bar',
                    stack: 'total',
                    data: failData,
                    itemStyle: {
                        color: '#ef4444'
                    }
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
                axisLabel: {
                    interval: 0,
                    rotate: 30
                }
            },
            yAxis: {
                type: 'value',
                max: 100,
                axisLabel: {
                    formatter: '{value}%'
                }
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

async function loadRecentDefenseLogs() {
    try {
        const data = await http.get('/defense/list?pageSize=10');
        
        const tbody = document.getElementById('recentDefenseBody');
        
        if (!data || !data.list || data.list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
            return;
        }
        
        tbody.innerHTML = data.list.map(item => `
            <tr>
                <td>${dateFormat.format(item.createTime)}</td>
                <td>${renderDefenseType(item.defenseType)}</td>
                <td title="${item.defenseTarget || '-'}">${truncateText(item.defenseTarget || '-', 20)}</td>
                <td>${renderDefenseAction(item.defenseAction)}</td>
                <td>${item.executeStatus === 1 ? '<span class="tag success">成功</span>' : '<span class="tag danger">失败</span>'}</td>
                <td>${item.eventId ? `<a href="/event?id=${item.eventId}" class="event-link">${item.eventId.substring(0, 8)}...</a>` : '-'}</td>
                <td>
                    <a href="/defense" class="btn btn-link btn-sm">详情</a>
                </td>
            </tr>
        `).join('');
        
    } catch (error) {
        console.error('加载最近防御日志失败:', error);
    }
}

function renderDefenseType(type) {
    const typeMap = {
        'BLOCK_IP': '<span class="tag danger">IP封禁</span>',
        'RATE_LIMIT': '<span class="tag warning">限流</span>',
        'REDIRECT': '<span class="tag info">重定向</span>',
        'CAPTCHA': '<span class="tag info">验证码</span>'
    };
    return typeMap[type] || type;
}

function renderDefenseAction(action) {
    const actionMap = {
        'BLOCK': '阻断',
        'LIMIT': '限速',
        'REDIRECT': '重定向',
        'CHALLENGE': '挑战'
    };
    return actionMap[action] || action;
}

function truncateText(text, maxLength) {
    if (!text) return '-';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
}

function refreshData() {
    loadDefenseStatistics();
    loadDefenseTrend();
    loadSuccessRateByType();
    loadRecentDefenseLogs();
}

function exportReport() {
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    window.location.href = `/defense/export-report?startDate=${startDate}&endDate=${endDate}`;
}
