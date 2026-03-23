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
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
                    <td>${item.id || '-'}</td>
                    ${cell.renderCell(item.ruleName, { maxLength: 40 })}
                    <td>${cell.renderAttackType(item.attackType)}</td>
                    ${cell.renderCell(item.ruleContent, { maxLength: 60 })}
                    <td>${cell.renderRiskLevel(item.riskLevel)}</td>
                    <td>${cell.renderStatus(item.enabled)}</td>
                    ${cell.renderActionCell([
                        cell.renderToggleButtonItem(item.enabled, item.id, '禁用', '启用', 'toggleRuleStatus'),
                        { text: '编辑', type: 'primary', onClick: `editRule(${item.id})` },
                        { text: '删除', type: 'danger', onClick: `deleteRule(${item.id})` }
                    ])}
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

function toggleRuleStatus(id, currentStatus) {
    const newStatus = currentStatus === 1 ? 0 : 1;
    const actionText = newStatus === 1 ? '启用' : '禁用';
    
    if (!confirm(`确定要${actionText}该规则吗？`)) {
        return;
    }
    
    http.put('/rule/toggle', { id: id, enabled: newStatus })
        .then(result => {
            message.success(`${actionText}成功`);
            ruleTable.refresh();
        })
        .catch(error => {
            message.error(`${actionText}失败: ` + (error.message || '未知错误'));
        });
}

function editRule(id) {
    http.get('/rule/detail/' + id)
        .then(result => {
            if (result) {
                document.getElementById('ruleId').value = result.id;
                document.getElementById('ruleNameInput').value = result.ruleName || '';
                document.getElementById('attackTypeInput').value = result.attackType || '';
                document.getElementById('ruleContentInput').value = result.ruleContent || '';
                document.getElementById('descriptionInput').value = result.description || '';
                document.getElementById('riskLevelInput').value = result.riskLevel || 'MEDIUM';
                document.getElementById('enabledInput').value = result.enabled !== undefined ? result.enabled : 1;
                document.getElementById('priorityInput').value = result.priority || 100;
                
                document.getElementById('ruleModalTitle').textContent = '编辑规则';
                document.getElementById('ruleModal').style.display = 'flex';
            }
        })
        .catch(error => {
            message.error('获取规则详情失败: ' + (error.message || '未知错误'));
        });
}

function deleteRule(id) {
    if (!confirm('确定要删除该规则吗？此操作不可恢复。')) {
        return;
    }
    
    http.delete('/rule/delete/' + id)
        .then(result => {
            message.success('删除成功');
            ruleTable.refresh();
        })
        .catch(error => {
            message.error('删除失败: ' + (error.message || '未知错误'));
        });
}

function openAddRuleModal() {
    document.getElementById('ruleId').value = '';
    document.getElementById('ruleNameInput').value = '';
    document.getElementById('attackTypeInput').value = '';
    document.getElementById('ruleContentInput').value = '';
    document.getElementById('descriptionInput').value = '';
    document.getElementById('riskLevelInput').value = 'MEDIUM';
    document.getElementById('enabledInput').value = '1';
    document.getElementById('priorityInput').value = '100';
    
    document.getElementById('ruleModalTitle').textContent = '新增规则';
    document.getElementById('ruleModal').style.display = 'flex';
}

function closeRuleModal() {
    document.getElementById('ruleModal').style.display = 'none';
}

function saveRule() {
    const id = document.getElementById('ruleId').value;
    const ruleName = document.getElementById('ruleNameInput').value.trim();
    const attackType = document.getElementById('attackTypeInput').value.trim();
    const ruleContent = document.getElementById('ruleContentInput').value.trim();
    const description = document.getElementById('descriptionInput').value.trim();
    const riskLevel = document.getElementById('riskLevelInput').value;
    const enabled = parseInt(document.getElementById('enabledInput').value);
    const priority = parseInt(document.getElementById('priorityInput').value) || 100;
    
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
    
    const data = {
        ruleName: ruleName,
        attackType: attackType,
        ruleContent: ruleContent,
        description: description,
        riskLevel: riskLevel,
        enabled: enabled,
        priority: priority
    };
    
    const isEdit = id && id !== '';
    const url = isEdit ? '/rule/update' : '/rule/add';
    const method = isEdit ? 'put' : 'post';
    
    if (isEdit) {
        data.id = parseInt(id);
    }
    
    http[method](url, data)
        .then(result => {
            message.success(isEdit ? '更新成功' : '新增成功');
            closeRuleModal();
            ruleTable.refresh();
        })
        .catch(error => {
            message.error((isEdit ? '更新失败' : '新增失败') + ': ' + (error.message || '未知错误'));
        });
}

document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeRuleModal();
    }
});
