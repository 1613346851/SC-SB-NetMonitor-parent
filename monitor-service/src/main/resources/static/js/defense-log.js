/**
 * 防御日志页面 JavaScript
 * 展示防御日志列表，支持事件ID关联查看
 */

let defenseTable;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    const urlParams = new URLSearchParams(window.location.search);
    const eventIdFromUrl = urlParams.get('eventId');
    if (eventIdFromUrl) {
        document.getElementById('eventId').value = eventIdFromUrl;
    }
    
    initDefenseTable();
});

function initDefenseTable() {
    defenseTable = TableUtils.createInstance({
        instanceName: 'defenseTable',
        apiUrl: '/defense/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'defenseTableBody',
        paginationEl: 'pagination',
        colspan: 12,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            const eventIdLink = item.eventId 
                ? `<a href="/event?id=${item.eventId}" class="event-link" title="查看事件详情">${item.eventId.substring(0, 12)}...</a>`
                : '-';
            
            const isFirstTag = item.isFirst === 1 
                ? '<span class="tag info">是</span>'
                : '<span class="tag">否</span>';
            
            return `
                <tr>
                    <td>${item.id || '-'}</td>
                    <td>${dateFormat.format(item.createTime)}</td>
                    <td>${renderDefenseType(item.defenseType)}</td>
                    <td>${renderDefenseAction(item.defenseAction)}</td>
                    ${cell.renderCell(item.defenseTarget, { maxLength: 20 })}
                    ${cell.renderCell(item.defenseReason, { maxLength: 15 })}
                    <td>${isFirstTag}</td>
                    <td>${item.expireTime ? dateFormat.format(item.expireTime) : (item.defenseType === 'BLOCK_IP' ? '永久' : '-')}</td>
                    <td>${eventIdLink}</td>
                    <td>${item.executeStatus === 1 ? '<span class="tag success">成功</span>' : '<span class="tag danger">失败</span>'}</td>
                    <td>${renderOperator(item.operator)}</td>
                    ${cell.renderActionCell([
                        { text: '详情', type: 'primary', onClick: `viewDefenseDetail(${item.id})` }
                    ])}
                </tr>
            `;
        }
    });
    
    window.defenseTable = defenseTable;
    defenseTable.loadData();
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

function renderOperator(operator) {
    if (!operator) return '-';
    if (operator === 'SYSTEM') return '<span class="tag">系统</span>';
    if (operator === 'AUTO') return '<span class="tag info">自动</span>';
    return operator;
}

function truncateText(text, maxLength) {
    if (!text) return '-';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
}

function searchDefense() {
    const eventId = defenseTable.getSearchValue('eventId');
    const defenseType = defenseTable.getSearchSelectValue('defenseType');
    const executeStatus = defenseTable.getSearchSelectValue('executeStatus');
    const defenseTarget = defenseTable.getSearchValue('defenseTarget');
    const dateRange = defenseTable.getDateRangeValue('startDate', 'endDate');
    
    const params = {};
    if (eventId) params.eventId = eventId;
    if (defenseType) params.defenseType = defenseType;
    if (executeStatus !== '') params.executeStatus = executeStatus;
    if (defenseTarget) params.defenseTarget = defenseTarget;
    if (dateRange.startTime) params.startTime = dateRange.startTime;
    if (dateRange.endTime) params.endTime = dateRange.endTime;
    
    defenseTable.search(params);
}

function resetSearch() {
    document.getElementById('eventId').value = '';
    document.getElementById('defenseType').value = '';
    document.getElementById('executeStatus').value = '';
    document.getElementById('defenseTarget').value = '';
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    defenseTable.resetSearch();
}

async function viewDefenseDetail(id) {
    try {
        const detail = await http.get(`/defense/${id}`);
        
        const eventIdHtml = detail.eventId 
            ? `<a href="/event?id=${detail.eventId}" class="event-link">${detail.eventId}</a>`
            : '-';
        
        const isFirstHtml = detail.isFirst === 1 
            ? '<span class="tag info">首次防御</span>'
            : '<span class="tag">非首次</span>';
        
        const content = document.getElementById('defenseDetailContent');
        content.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div>
                    <p><strong>日志 ID:</strong> ${detail.id}</p>
                    <p><strong>防御时间:</strong> ${dateFormat.format(detail.createTime)}</p>
                    <p><strong>防御类型:</strong> ${renderDefenseType(detail.defenseType)}</p>
                    <p><strong>防御动作:</strong> ${renderDefenseAction(detail.defenseAction)}</p>
                    <p><strong>是否首次:</strong> ${isFirstHtml}</p>
                </div>
                <div>
                    <p><strong>关联事件:</strong> ${eventIdHtml}</p>
                    <p><strong>执行状态:</strong> ${detail.executeStatus === 1 ? '<span class="tag success">成功</span>' : '<span class="tag danger">失败</span>'}</p>
                    <p><strong>过期时间:</strong> ${detail.expireTime ? dateFormat.format(detail.expireTime) : (detail.defenseType === 'BLOCK_IP' ? '永久' : '-')}</p>
                    <p><strong>操作者:</strong> ${renderOperator(detail.operator)}</p>
                </div>
            </div>
            <div class="mt-24">
                <p><strong>防御目标:</strong></p>
                <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px;">${detail.defenseTarget || '-'}</pre>
            </div>
            <div class="mt-16">
                <p><strong>防御原因:</strong></p>
                <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px;">${detail.defenseReason || '-'}</pre>
            </div>
        `;
        
        document.getElementById('defenseDetailModal').style.display = 'flex';
        
        document.getElementById('viewEventBtn').style.display = detail.eventId ? 'inline-block' : 'none';
    } catch (error) {
        console.error('加载防御详情失败:', error);
    }
}

function closeDefenseDetail() {
    document.getElementById('defenseDetailModal').style.display = 'none';
}

function viewEventFromDetail() {
    const eventId = document.querySelector('#defenseDetailContent .event-link')?.textContent;
    if (eventId) {
        window.location.href = `/event?id=${eventId}`;
    }
}

document.getElementById('defenseDetailModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeDefenseDetail();
    }
});
