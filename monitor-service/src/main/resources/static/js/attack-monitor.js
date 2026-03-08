/**
 * 攻击监测页面 JavaScript
 * 负责攻击数据加载、筛选、分页、详情查看、处理等功能
 */

let currentPage = 1;
const pageSize = 10;
let searchParams = {};
let currentAttackId = null;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    loadAttackData();
    
    setInterval(() => {
        if (!searchParams.handled || searchParams.handled === '0') {
            loadAttackData();
        }
    }, 5000);
});

async function loadAttackData() {
    try {
        const params = {
            page: currentPage,
            size: pageSize,
            ...searchParams
        };

        const result = await http.get('/attack/list', params);
        
        renderAttackTable(result.list || []);
        renderPagination(result.total || 0);
    } catch (error) {
        console.error('加载攻击数据失败:', error);
    }
}

function renderAttackTable(data) {
    const tbody = document.getElementById('attackTableBody');
    
    if (!data || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = data.map(item => `
        <tr>
            <td>${item.id || '-'}</td>
            <td>${dateFormat.format(item.attackTime)}</td>
            <td>${item.sourceIp || '-'}</td>
            <td>${item.targetIp || '-'}</td>
            <td>${tableRenderer.renderAttackType(item.attackType)}</td>
            <td>${tableRenderer.renderRiskLevel(item.riskLevel)}</td>
            <td>${item.confidence ? item.confidence + '%' : '-'}</td>
            <td>${tableRenderer.renderStatus(item.handled)}</td>
            <td>
                <button class="btn btn-primary btn-sm" onclick="viewAttackDetail(${item.id})">详情</button>
                ${item.handled === 0 ? `<button class="btn btn-success btn-sm" onclick="handleAttackDirect(${item.id})">处理</button>` : ''}
            </td>
        </tr>
    `).join('');
}

function renderPagination(total) {
    const totalPages = Math.ceil(total / pageSize);
    const paginationEl = document.getElementById('pagination');
    
    let html = `
        <span class="pagination-item ${currentPage === 1 ? 'disabled' : ''}" 
              onclick="goPage(${currentPage - 1})">上一页</span>
    `;
    
    for (let i = 1; i <= totalPages; i++) {
        if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
            html += `
                <span class="pagination-item ${i === currentPage ? 'active' : ''}" 
                      onclick="goPage(${i})">${i}</span>
            `;
        } else if (i === currentPage - 2 || i === currentPage + 2) {
            html += `<span class="pagination-item">...</span>`;
        }
    }
    
    html += `
        <span class="pagination-item ${currentPage === totalPages ? 'disabled' : ''}" 
              onclick="goPage(${currentPage + 1})">下一页</span>
        <span class="pagination-item">共 ${total} 条</span>
    `;
    
    paginationEl.innerHTML = html;
}

function goPage(page) {
    const totalPages = Math.ceil((searchParams.total || 10) / pageSize);
    
    if (page < 1 || page > totalPages || page === currentPage) {
        return;
    }
    
    currentPage = page;
    loadAttackData();
}

function searchAttacks() {
    const sourceIp = document.getElementById('sourceIp').value.trim();
    const attackType = document.getElementById('attackType').value;
    const riskLevel = document.getElementById('riskLevel').value;
    const handled = document.getElementById('handled').value;
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    searchParams = {};
    
    if (sourceIp) searchParams.sourceIp = sourceIp;
    if (attackType) searchParams.attackType = attackType;
    if (riskLevel) searchParams.riskLevel = riskLevel;
    if (handled !== '') searchParams.handled = handled;
    if (startDate) searchParams.startDate = startDate + ' 00:00:00';
    if (endDate) searchParams.endDate = endDate + ' 23:59:59';
    
    currentPage = 1;
    loadAttackData();
}

function resetSearch() {
    document.getElementById('sourceIp').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('riskLevel').value = '';
    document.getElementById('handled').value = '';
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    searchParams = {};
    currentPage = 1;
    loadAttackData();
}

async function viewAttackDetail(id) {
    try {
        const detail = await http.get(`/attack/${id}`);
        currentAttackId = id;
        
        const content = document.getElementById('attackDetailContent');
        content.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div>
                    <p><strong>攻击时间:</strong> ${dateFormat.format(detail.attackTime)}</p>
                    <p><strong>源 IP:</strong> ${detail.sourceIp}</p>
                    <p><strong>目标 IP:</strong> ${detail.targetIp}</p>
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
        loadAttackData();
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
        loadAttackData();
    } catch (error) {
        console.error('处理攻击失败:', error);
    }
}

document.getElementById('attackDetailModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeAttackDetail();
    }
});
