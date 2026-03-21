/**
 * 攻击事件态势感知页面 JavaScript
 * 展示攻击事件时间线、统计信息和关联攻击记录
 */

let eventTable;
let currentEventId = null;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    const urlParams = new URLSearchParams(window.location.search);
    const eventIdFromUrl = urlParams.get('id');
    if (eventIdFromUrl) {
        loadEventDetail(eventIdFromUrl);
    }
    
    initEventTable();
    loadEventStatistics();
    
    setInterval(() => {
        loadEventStatistics();
    }, 10000);
});

function initEventTable() {
    eventTable = TableUtils.createInstance({
        instanceName: 'eventTable',
        apiUrl: '/api/event/list',
        pageSize: 10,
        defaultSortField: 'startTime',
        defaultSortOrder: 'desc',
        tableBodyEl: 'eventTableBody',
        paginationEl: 'pagination',
        colspan: 10,
        renderRow: function(item) {
            const statusTag = item.status === 0 
                ? '<span class="tag warning">进行中</span>'
                : '<span class="tag success">已结束</span>';
            
            const duration = formatDuration(item.durationSeconds);
            
            return `
                <tr onclick="loadEventDetail('${item.eventId}')" style="cursor: pointer;">
                    <td><span class="event-id" title="${item.eventId}">${item.eventId.substring(0, 12)}...</span></td>
                    <td>${dateFormat.format(item.startTime)}</td>
                    <td>${item.sourceIp || '-'}</td>
                    <td>${tableRenderer.renderAttackType(item.attackType)}</td>
                    <td>${tableRenderer.renderRiskLevel(item.riskLevel)}</td>
                    <td>${duration}</td>
                    <td>${item.totalRequests || 0}</td>
                    <td>${item.peakRps || 0}</td>
                    <td>${statusTag}</td>
                    <td>
                        <button class="btn btn-primary btn-sm" onclick="event.stopPropagation(); loadEventDetail('${item.eventId}')">详情</button>
                    </td>
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

function searchEvents() {
    const eventId = eventTable.getSearchValue('eventId');
    const sourceIp = eventTable.getSearchValue('sourceIp');
    const attackType = eventTable.getSearchSelectValue('attackType');
    const riskLevel = eventTable.getSearchSelectValue('riskLevel');
    const status = eventTable.getSearchSelectValue('status');
    const dateRange = eventTable.getDateRangeValue('startDate', 'endDate');
    
    const params = {};
    if (eventId) params.eventId = eventId;
    if (sourceIp) params.sourceIp = sourceIp;
    if (attackType) params.attackType = attackType;
    if (riskLevel) params.riskLevel = riskLevel;
    if (status !== '') params.status = status;
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
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    eventTable.resetSearch();
}

async function loadEventStatistics() {
    try {
        const stats = await http.get('/api/event/statistics');
        
        document.getElementById('totalEvents').textContent = stats.totalEvents || 0;
        document.getElementById('ongoingEvents').textContent = stats.ongoingEvents || 0;
        document.getElementById('endedEvents').textContent = stats.endedEvents || 0;
        document.getElementById('avgDuration').textContent = formatDuration(stats.avgDuration) || '-';
    } catch (error) {
        console.error('加载事件统计失败:', error);
    }
}

async function loadEventDetail(eventId) {
    try {
        const event = await http.get(`/api/event/eventId/${eventId}`);
        currentEventId = eventId;
        
        const detailContent = document.getElementById('eventDetailContent');
        
        const statusHtml = event.status === 0 
            ? '<span class="tag warning">进行中</span>'
            : '<span class="tag success">已结束</span>';
        
        const defenseSuccessHtml = event.defenseSuccess === null 
            ? '-'
            : event.defenseSuccess === 1 
                ? '<span class="tag success">成功</span>'
                : '<span class="tag danger">失败</span>';
        
        detailContent.innerHTML = `
            <div class="event-detail-header">
                <h3>事件详情</h3>
                <span class="event-full-id">${event.eventId}</span>
            </div>
            
            <div class="event-detail-grid">
                <div class="detail-section">
                    <h4>基本信息</h4>
                    <div class="detail-item">
                        <label>源 IP:</label>
                        <span>${event.sourceIp}</span>
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
                        <span>${event.peakRps || 0}</span>
                    </div>
                    <div class="detail-item">
                        <label>攻击节点数:</label>
                        <span>${event.attackCount || 0}</span>
                    </div>
                    <div class="detail-item">
                        <label>置信度:</label>
                        <span>${event.confidenceEnd ? event.confidenceEnd + '%' : '-'}</span>
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
        
        document.getElementById('eventDetailPanel').style.display = 'block';
        
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
    document.getElementById('eventDetailPanel').style.display = 'none';
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
