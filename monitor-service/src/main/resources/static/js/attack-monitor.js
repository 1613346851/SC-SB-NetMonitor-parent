/**
 * 攻击监测页面 JavaScript
 * 使用 TableUtils 通用组件
 * 支持事件ID筛选和关联查看
 */

let attackTable;
let currentAttackId = null;
let currentEventId = null;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    const urlParams = new URLSearchParams(window.location.search);
    const eventIdFromUrl = urlParams.get('eventId');
    const attackIdFromUrl = urlParams.get('attackId');
    
    if (eventIdFromUrl) {
        document.getElementById('eventId').value = eventIdFromUrl;
    }
    
    initAttackTable();
    
    if (attackIdFromUrl) {
        setTimeout(() => {
            viewAttackDetail(attackIdFromUrl);
        }, 500);
    }
    
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
        case 'ATTACK_RECORD':
            handleAttackRecordUpdate(message.data);
            break;
    }
}

function handleAttackRecordUpdate(attackData) {
    if (!attackData || !attackTable) return;
    
    const handledSelect = document.getElementById('handled');
    if (handledSelect && handledSelect.value === '1') {
        return;
    }
    
    const currentData = attackTable.getCurrentData() || [];
    const existingIndex = currentData.findIndex(item => item.id === attackData.id);
    
    if (existingIndex >= 0) {
        currentData[existingIndex] = attackData;
    } else {
        currentData.unshift(attackData);
        if (currentData.length > attackTable.options.pageSize) {
            currentData.pop();
        }
    }
    
    attackTable.updateData(currentData);
}

function initAttackTable() {
    attackTable = TableUtils.createInstance({
        instanceName: 'attackTable',
        apiUrl: '/attack/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'attackTableBody',
        paginationEl: 'pagination',
        colspan: 10,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            const buttons = [
                { text: '详情', type: 'primary', onClick: `viewAttackDetail(${item.id})` },
                { text: '处理', type: 'success', onClick: `handleAttackDirect(${item.id})`, visible: item.handled === 0 },
                { text: '事件', type: 'info', onClick: `window.location.href='/event?id=${encodeURIComponent(item.eventId)}'`, visible: !!item.eventId }
            ];
            
            return `
                <tr>
                    <td>${item.id || '-'}</td>
                    ${cell.renderCell(item.eventId, { maxLength: 16 })}
                    <td>${dateFormat.format(item.createTime)}</td>
                    <td>${item.sourceIp || '-'}</td>
                    ${cell.renderCell(item.targetUri, { maxLength: 30 })}
                    <td>${cell.renderAttackType(item.attackType)}</td>
                    <td>${cell.renderRiskLevel(item.riskLevel)}</td>
                    <td>${item.confidence ? item.confidence + '%' : '-'}</td>
                    <td>${cell.renderStatus(item.handled, 'handle')}</td>
                    ${cell.renderActionCell(buttons)}
                </tr>
            `;
        }
    });
    
    window.attackTable = attackTable;
    attackTable.loadData();
}

function truncateText(text, maxLength) {
    if (!text) return '-';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
}

function searchAttacks() {
    const eventId = attackTable.getSearchValue('eventId');
    const sourceIp = attackTable.getSearchValue('sourceIp');
    const attackType = attackTable.getSearchSelectValue('attackType');
    const riskLevel = attackTable.getSearchSelectValue('riskLevel');
    const handled = attackTable.getSearchSelectValue('handled');
    const dateRange = attackTable.getDateRangeValue('startDate', 'endDate');
    
    const params = {};
    if (eventId) params.eventId = eventId;
    if (sourceIp) params.sourceIp = sourceIp;
    if (attackType) params.attackType = attackType;
    if (riskLevel) params.riskLevel = riskLevel;
    if (handled !== '') params.handled = handled;
    if (dateRange.startTime) params.startTime = dateRange.startTime;
    if (dateRange.endTime) params.endTime = dateRange.endTime;
    
    attackTable.search(params);
}

function resetSearch() {
    document.getElementById('eventId').value = '';
    document.getElementById('sourceIp').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('riskLevel').value = '';
    document.getElementById('handled').value = '';
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    attackTable.resetSearch();
}

function exportAttacks() {
    attackTable.exportCSV('/attack/export', 'attack_export.csv');
}

async function viewAttackDetail(id) {
    try {
        const detail = await http.get(`/attack/${id}`);
        currentAttackId = id;
        currentEventId = detail.eventId || null;
        
        const eventIdHtml = detail.eventId 
            ? `<a href="/event?id=${detail.eventId}" class="event-link">${detail.eventId}</a>`
            : '-';
        
        const content = document.getElementById('attackDetailContent');
        content.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div>
                    <p><strong>攻击记录 ID:</strong> ${detail.id || '-'}</p>
                    <p><strong>事件 ID:</strong> ${eventIdHtml}</p>
                    <p><strong>攻击时间:</strong> ${dateFormat.format(detail.createTime)}</p>
                    <p><strong>源 IP:</strong> ${detail.sourceIp}</p>
                    <p><strong>目标 URI:</strong> ${detail.targetUri || '-'}</p>
                    <p><strong>攻击类型:</strong> ${tableRenderer.renderAttackType(detail.attackType)}</p>
                    <p><strong>风险等级:</strong> ${tableRenderer.renderRiskLevel(detail.riskLevel)}</p>
                </div>
                <div>
                    <p><strong>置信度:</strong> ${detail.confidence ? detail.confidence + '%' : '-'}</p>
                    <p><strong>处理状态:</strong> ${tableRenderer.renderStatus(detail.handled)}</p>
                    <p><strong>命中规则 ID:</strong> ${detail.ruleId || '-'}</p>
                    <p><strong>关联流量 ID:</strong> ${detail.trafficId || '-'}</p>
                    <p><strong>处理时间:</strong> ${detail.handleTime ? dateFormat.format(detail.handleTime) : '-'}</p>
                    <p><strong>处理备注:</strong> ${detail.handleRemark || '-'}</p>
                </div>
            </div>
            <div class="mt-24">
                <p><strong>攻击内容:</strong></p>
                <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; max-height: 300px; overflow-y: auto;">${detail.attackContent || '-'}</pre>
            </div>
        `;
        
        document.getElementById('attackDetailModal').style.display = 'flex';
        
        document.getElementById('handleBtn').style.display = detail.handled === 0 ? 'inline-block' : 'none';
        document.getElementById('viewEventBtn').style.display = detail.eventId ? 'inline-block' : 'none';
    } catch (error) {
        console.error('加载攻击详情失败:', error);
    }
}

function closeAttackDetail() {
    document.getElementById('attackDetailModal').style.display = 'none';
    currentAttackId = null;
}

async function handleAttackDirect(id) {
    try {
        await http.put(`/attack/${id}/handle`, {
            handleRemark: '已手动处理'
        });
        
        message.success('处理成功');
        attackTable.loadData();
    } catch (error) {
        console.error('处理攻击失败:', error);
    }
}

async function handleAttack() {
    if (!currentAttackId) return;
    
    try {
        await http.put(`/attack/${currentAttackId}/handle`, {
            handleRemark: '已手动处理'
        });
        
        message.success('处理成功');
        closeAttackDetail();
        attackTable.loadData();
    } catch (error) {
        console.error('处理攻击失败:', error);
    }
}

function viewEventDetail() {
    if (!currentEventId) {
        message.error('事件ID不存在');
        return;
    }
    window.location.href = `/event?id=${encodeURIComponent(currentEventId)}`;
}

document.getElementById('attackDetailModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeAttackDetail();
    }
});
