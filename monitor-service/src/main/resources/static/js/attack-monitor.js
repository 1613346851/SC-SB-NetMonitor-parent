/**
 * 攻击监测页面 JavaScript
 * 使用 TableUtils 通用组件
 */

let attackTable;
let currentAttackId = null;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    initAttackTable();
    
    setInterval(() => {
        const handledSelect = document.getElementById('handled');
        if (!handledSelect || handledSelect.value === '' || handledSelect.value === '0') {
            attackTable.loadData();
        }
    }, 5000);
});

function initAttackTable() {
    attackTable = TableUtils.createInstance({
        instanceName: 'attackTable',
        apiUrl: '/attack/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'attackTableBody',
        paginationEl: 'pagination',
        colspan: 9,
        renderRow: function(item) {
            return `
                <tr>
                    <td><a href="javascript:void(0)" onclick="attackTable.sort('id')">${item.id || '-'}</a></td>
                    <td><a href="javascript:void(0)" onclick="attackTable.sort('createTime')">${dateFormat.format(item.createTime)}</a></td>
                    <td>${item.sourceIp || '-'}</td>
                    <td>${item.targetUri || '-'}</td>
                    <td>${tableRenderer.renderAttackType(item.attackType)}</td>
                    <td>${tableRenderer.renderRiskLevel(item.riskLevel)}</td>
                    <td>${item.confidence ? item.confidence + '%' : '-'}</td>
                    <td>${tableRenderer.renderStatus(item.handled)}</td>
                    <td>
                        <button class="btn btn-primary btn-sm" onclick="viewAttackDetail(${item.id})">详情</button>
                        ${item.handled === 0 ? `<button class="btn btn-success btn-sm" onclick="handleAttackDirect(${item.id})">处理</button>` : ''}
                    </td>
                </tr>
            `;
        }
    });
    
    window.attackTable = attackTable;
    attackTable.loadData();
}

function searchAttacks() {
    const sourceIp = attackTable.getSearchValue('sourceIp');
    const attackType = attackTable.getSearchSelectValue('attackType');
    const riskLevel = attackTable.getSearchSelectValue('riskLevel');
    const handled = attackTable.getSearchSelectValue('handled');
    const dateRange = attackTable.getDateRangeValue('startDate', 'endDate');
    
    const params = {};
    if (sourceIp) params.sourceIp = sourceIp;
    if (attackType) params.attackType = attackType;
    if (riskLevel) params.riskLevel = riskLevel;
    if (handled !== '') params.handled = handled;
    if (dateRange.startTime) params.startTime = dateRange.startTime;
    if (dateRange.endTime) params.endTime = dateRange.endTime;
    
    attackTable.search(params);
}

function resetSearch() {
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
        
        const content = document.getElementById('attackDetailContent');
        content.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div>
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

document.getElementById('attackDetailModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeAttackDetail();
    }
});
