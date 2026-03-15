/**
 * 规则管理页面 JavaScript
 * 负责规则数据加载、筛选、分页、新增、编辑、删除等功能
 */

let currentPage = 1;
const pageSize = 10;
let searchParams = {};

document.addEventListener('DOMContentLoaded', function() {
    loadRuleData();
});

async function loadRuleData() {
    try {
        const params = {
            page: currentPage,
            size: pageSize,
            ...searchParams
        };

        const result = await http.get('/rule/list', params);
        
        renderRuleTable(result.list || result || []);
        renderPagination(result.total || 0);
    } catch (error) {
        console.error('加载规则数据失败:', error);
    }
}

function renderRuleTable(data) {
    const tbody = document.getElementById('ruleTableBody');
    
    if (!data || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = data.map(item => `
        <tr>
            <td>${item.id || '-'}</td>
            <td>${item.ruleName || '-'}</td>
            <td>${tableRenderer.renderAttackType(item.attackType)}</td>
            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis;">${item.ruleContent || '-'}</td>
            <td>${tableRenderer.renderRiskLevel(item.riskLevel)}</td>
            <td>${item.enabled === 1 ? '<span class="tag success">启用</span>' : '<span class="tag info">禁用</span>'}</td>
            <td>
                <button class="btn btn-primary btn-sm" onclick="editRule(${item.id})">编辑</button>
                <button class="btn btn-${item.enabled === 1 ? 'warning' : 'success'} btn-sm" onclick="toggleRuleStatus(${item.id}, ${item.enabled})">
                    ${item.enabled === 1 ? '禁用' : '启用'}
                </button>
                <button class="btn btn-danger btn-sm" onclick="deleteRule(${item.id})">删除</button>
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
    loadRuleData();
}

function searchRules() {
    const ruleName = document.getElementById('ruleName').value.trim();
    const attackType = document.getElementById('attackType').value;
    const enabled = document.getElementById('enabled').value;
    
    searchParams = {};
    
    if (ruleName) searchParams.ruleName = ruleName;
    if (attackType) searchParams.attackType = attackType;
    if (enabled !== '') searchParams.enabled = enabled;
    
    currentPage = 1;
    loadRuleData();
}

function resetSearch() {
    document.getElementById('ruleName').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('enabled').value = '';
    
    searchParams = {};
    currentPage = 1;
    loadRuleData();
}

function showAddRuleModal() {
    document.getElementById('ruleModalTitle').textContent = '新增规则';
    document.getElementById('ruleForm').reset();
    document.getElementById('ruleId').value = '';
    document.getElementById('ruleModal').style.display = 'flex';
}

async function editRule(id) {
    try {
        const rule = await http.get(`/rule/${id}`);
        
        document.getElementById('ruleModalTitle').textContent = '编辑规则';
        document.getElementById('ruleId').value = rule.id;
        document.getElementById('ruleNameInput').value = rule.ruleName;
        document.getElementById('attackTypeInput').value = rule.attackType;
        document.getElementById('ruleExpressionInput').value = rule.ruleContent || '';
        document.getElementById('riskLevelInput').value = rule.riskLevel;
        document.getElementById('confidenceThresholdInput').value = rule.confidenceThreshold || 60;
        document.getElementById('enabledInput').value = rule.enabled;
        
        document.getElementById('ruleModal').style.display = 'flex';
    } catch (error) {
        console.error('加载规则详情失败:', error);
    }
}

function closeRuleModal() {
    document.getElementById('ruleModal').style.display = 'none';
}

async function saveRule() {
    const ruleId = document.getElementById('ruleId').value;
    const ruleData = {
        ruleName: document.getElementById('ruleNameInput').value,
        attackType: document.getElementById('attackTypeInput').value,
        ruleContent: document.getElementById('ruleExpressionInput').value,
        riskLevel: document.getElementById('riskLevelInput').value,
        enabled: parseInt(document.getElementById('enabledInput').value)
    };

    if (!ruleData.ruleName || !ruleData.attackType || !ruleData.ruleContent || !ruleData.riskLevel) {
        message.error('请填写所有必填字段');
        return;
    }

    try {
        if (ruleId) {
            ruleData.id = parseInt(ruleId);
            await http.post('/rule/update', ruleData);
            message.success('更新成功');
        } else {
            await http.post('/rule/add', ruleData);
            message.success('创建成功');
        }
        
        closeRuleModal();
        loadRuleData();
    } catch (error) {
        console.error('保存规则失败:', error);
    }
}

async function toggleRuleStatus(id, currentStatus) {
    try {
        await http.put(`/rule/${id}/toggle`, {
            enabled: currentStatus === 1 ? 0 : 1
        });
        
        message.success('操作成功');
        loadRuleData();
    } catch (error) {
        console.error('切换规则状态失败:', error);
    }
}

async function deleteRule(id) {
    if (!confirm('确定要删除这条规则吗？')) {
        return;
    }

    try {
        await http.delete(`/rule/${id}`);
        message.success('删除成功');
        loadRuleData();
    } catch (error) {
        console.error('删除规则失败:', error);
    }
}

document.getElementById('ruleModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeRuleModal();
    }
});
