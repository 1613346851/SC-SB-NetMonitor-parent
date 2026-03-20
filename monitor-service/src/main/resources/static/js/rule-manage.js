let ruleTable;

document.addEventListener('DOMContentLoaded', function() {
    initRuleTable();
});

function initRuleTable() {
    ruleTable = TableUtils.createInstance({
        instanceName: 'ruleTable',
        apiUrl: '/rule/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'ruleTableBody',
        paginationEl: 'pagination',
        colspan: 7,
        renderRow: function(item) {
            const actions = [
                { text: '编辑', class: 'btn-primary', onClick: `editRule(${item.id})` },
                { text: item.enabled === 1 ? '禁用' : '启用', class: item.enabled === 1 ? 'btn-warning' : 'btn-success', onClick: `toggleRuleStatus(${item.id}, ${item.enabled})` },
                { text: '删除', class: 'btn-danger', onClick: `deleteRule(${item.id})` }
            ];
            
            return `
                <tr>
                    <td>${item.id || '-'}</td>
                    <td>${tableRenderer.renderText(item.ruleName)}</td>
                    <td>${tableRenderer.renderAttackType(item.attackType)}</td>
                    <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis;" title="${tableRenderer.escapeHtml(item.ruleContent || '')}">${tableRenderer.renderText(item.ruleContent, 30)}</td>
                    <td>${tableRenderer.renderRiskLevel(item.riskLevel)}</td>
                    <td>${item.enabled === 1 ? '<span class="tag success">启用</span>' : '<span class="tag info">禁用</span>'}</td>
                    <td class="action-btns">${tableRenderer.renderActionsSmart(actions, { maxVisible: 2 })}</td>
                </tr>
            `;
        }
    });
    
    window.ruleTable = ruleTable;
    ruleTable.loadData();
}

function searchRules() {
    const ruleName = ruleTable.getSearchValue('ruleName');
    const attackType = ruleTable.getSearchSelectValue('attackType');
    const enabled = ruleTable.getSearchSelectValue('enabled');
    
    const params = {};
    if (ruleName) params.ruleName = ruleName;
    if (attackType) params.attackType = attackType;
    if (enabled !== '') params.enabled = enabled;
    
    ruleTable.search(params);
}

function resetSearch() {
    document.getElementById('ruleName').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('enabled').value = '';
    ruleTable.resetSearch();
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
        message.error('加载规则详情失败');
    }
}

function closeRuleModal() {
    document.getElementById('ruleModal').style.display = 'none';
}

async function saveRule() {
    const id = document.getElementById('ruleId').value;
    const ruleName = document.getElementById('ruleNameInput').value.trim();
    const attackType = document.getElementById('attackTypeInput').value;
    const ruleContent = document.getElementById('ruleExpressionInput').value.trim();
    const riskLevel = document.getElementById('riskLevelInput').value;
    const confidenceThreshold = parseInt(document.getElementById('confidenceThresholdInput').value) || 60;
    const enabled = parseInt(document.getElementById('enabledInput').value);
    
    if (!ruleName) {
        message.error('请输入规则名称');
        return;
    }
    
    if (!attackType) {
        message.error('请选择攻击类型');
        return;
    }
    
    if (!ruleContent) {
        message.error('请输入规则表达式');
        return;
    }
    
    const params = {
        ruleName,
        attackType,
        ruleContent,
        riskLevel,
        confidenceThreshold,
        enabled
    };
    
    try {
        if (id) {
            params.id = parseInt(id);
            await http.put('/rule/update', params);
            message.success('更新成功');
        } else {
            await http.post('/rule/add', params);
            message.success('创建成功');
        }
        closeRuleModal();
        ruleTable.loadData();
    } catch (error) {
        console.error('保存规则失败:', error);
        message.error(error.message || '保存失败');
    }
}

async function toggleRuleStatus(id, currentStatus) {
    const newStatus = currentStatus === 1 ? 0 : 1;
    const action = newStatus === 1 ? '启用' : '禁用';
    
    try {
        await http.put(`/rule/status/${id}?enabled=${newStatus}`);
        message.success(`${action}成功`);
        ruleTable.loadData();
    } catch (error) {
        console.error('操作失败:', error);
        message.error('操作失败');
    }
}

async function deleteRule(id) {
    if (!confirm('确定要删除该规则吗？')) {
        return;
    }
    
    try {
        await http.delete(`/rule/${id}`);
        message.success('删除成功');
        ruleTable.loadData();
    } catch (error) {
        console.error('删除失败:', error);
        message.error('删除失败');
    }
}

document.getElementById('ruleModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeRuleModal();
    }
});
