let ruleTable;
let whitelistTable;
let currentTab = 'rules';
let selectedRuleIds = [];
let selectedWhitelistIds = [];
let allVulnerabilities = [];
let selectedVulnIds = [];
let vulnSearchTimeout = null;

document.addEventListener('DOMContentLoaded', function() {
    loadRuleStats();
    initRuleTable();
    initWhitelistTable();
    initVulnSelectEvents();
});

function loadRuleStats() {
    http.get('/rule/stats')
        .then(function(stats) {
            document.getElementById('statTotalRules').textContent = stats.totalRules || 0;
            document.getElementById('statEnabledRules').textContent = stats.enabledRules || 0;
            document.getElementById('statDisabledRules').textContent = stats.disabledRules || 0;
            document.getElementById('statSqlInjectionRules').textContent = stats.sqlInjectionRules || 0;
            document.getElementById('statXssRules').textContent = stats.xssRules || 0;
        })
        .catch(function(error) {
            console.error('加载规则统计失败:', error);
        });
}

function loadWhitelistStats() {
    http.get('/whitelist/stats')
        .then(function(stats) {
            document.getElementById('statTotalWhitelists').textContent = stats.totalWhitelists || 0;
            document.getElementById('statEnabledWhitelists').textContent = stats.enabledWhitelists || 0;
            document.getElementById('statDisabledWhitelists').textContent = stats.disabledWhitelists || 0;
            document.getElementById('statPathWhitelists').textContent = stats.pathWhitelists || 0;
            document.getElementById('statIpWhitelists').textContent = stats.ipWhitelists || 0;
        })
        .catch(function(error) {
            console.error('加载白名单统计失败:', error);
        });
}

function switchTab(tab) {
    currentTab = tab;
    
    document.getElementById('tab-rules').classList.remove('active');
    document.getElementById('tab-whitelist').classList.remove('active');
    document.getElementById('tab-' + tab).classList.add('active');
    
    if (tab === 'rules') {
        document.getElementById('rules-content').style.display = 'block';
        document.getElementById('whitelist-content').style.display = 'none';
        loadRuleStats();
    } else {
        document.getElementById('rules-content').style.display = 'none';
        document.getElementById('whitelist-content').style.display = 'block';
        loadWhitelistStats();
        if (whitelistTable) {
            whitelistTable.refresh();
        }
    }
}

function initRuleTable() {
    ruleTable = TableUtils.createInstance({
        instanceName: 'ruleTable',
        apiUrl: '/rule/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'ruleTableBody',
        paginationEl: 'pagination',
        colspan: 8,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
                    <td class="checkbox-cell"><input type="checkbox" class="rule-checkbox" value="${item.id}" onchange="updateSelectedRuleIds()"></td>
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

function initWhitelistTable() {
    whitelistTable = TableUtils.createInstance({
        instanceName: 'whitelistTable',
        apiUrl: '/whitelist/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'whitelistTableBody',
        paginationEl: 'whitelistPagination',
        colspan: 8,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
                    <td class="checkbox-cell"><input type="checkbox" class="whitelist-checkbox" value="${item.id}" onchange="updateSelectedWhitelistIds()"></td>
                    <td>${item.id || '-'}</td>
                    <td>${renderWhitelistType(item.whitelistType)}</td>
                    ${cell.renderCell(item.whitelistValue, { maxLength: 50 })}
                    ${cell.renderCell(item.description, { maxLength: 40 })}
                    <td>${item.priority || 100}</td>
                    <td>${cell.renderStatus(item.enabled)}</td>
                    ${cell.renderActionCell([
                        cell.renderToggleButtonItem(item.enabled, item.id, '禁用', '启用', 'toggleWhitelistStatus'),
                        { text: '编辑', type: 'primary', onClick: `editWhitelist(${item.id})` },
                        { text: '删除', type: 'danger', onClick: `deleteWhitelist(${item.id})` }
                    ])}
                </tr>
            `;
        }
    });
    
    window.whitelistTable = whitelistTable;
}

function renderWhitelistType(type) {
    const typeMap = {
        'PATH': '<span class="tag info">路径白名单</span>',
        'HEADER': '<span class="tag warning">请求头白名单</span>',
        'IP': '<span class="tag success">IP白名单</span>'
    };
    return typeMap[type] || '<span class="tag">' + type + '</span>';
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

function searchWhitelists() {
    const whitelistType = whitelistTable.getSearchSelectValue('whitelistType');
    const whitelistValue = whitelistTable.getSearchValue('whitelistValue');
    const enabled = whitelistTable.getSearchSelectValue('whitelistEnabled');
    
    const params = {};
    if (whitelistType) params.whitelistType = whitelistType;
    if (whitelistValue) params.whitelistValue = whitelistValue;
    if (enabled !== '') params.enabled = enabled;
    
    whitelistTable.search(params);
}

function resetWhitelistSearch() {
    document.getElementById('whitelistType').value = '';
    document.getElementById('whitelistValue').value = '';
    document.getElementById('whitelistEnabled').value = '';
    whitelistTable.resetSearch();
}

function toggleRuleStatus(id, currentStatus) {
    const newStatus = currentStatus === 1 ? 0 : 1;
    const actionText = newStatus === 1 ? '启用' : '禁用';
    
    if (!confirm(`确定要${actionText}该规则吗？`)) {
        return;
    }
    
    http.put('/rule/' + id + '/toggle')
        .then(result => {
            message.success(`${actionText}成功`);
            ruleTable.refresh();
        })
        .catch(error => {
            message.error(`${actionText}失败: ` + (error.message || '未知错误'));
        });
}

function toggleWhitelistStatus(id, currentStatus) {
    const newStatus = currentStatus === 1 ? 0 : 1;
    const actionText = newStatus === 1 ? '启用' : '禁用';
    
    if (!confirm(`确定要${actionText}该白名单吗？`)) {
        return;
    }
    
    http.put('/whitelist/' + id + '/toggle')
        .then(result => {
            message.success(`${actionText}成功`);
            whitelistTable.refresh();
        })
        .catch(error => {
            message.error(`${actionText}失败: ` + (error.message || '未知错误'));
        });
}

function editRule(id) {
    http.get('/rule/' + id)
        .then(result => {
            if (result) {
                const rule = result.rule;
                const vulnIds = result.vulnerabilityIds || [];
                
                document.getElementById('ruleId').value = rule.id;
                document.getElementById('ruleNameInput').value = rule.ruleName || '';
                document.getElementById('attackTypeInput').value = rule.attackType || '';
                document.getElementById('ruleExpressionInput').value = rule.ruleContent || '';
                document.getElementById('descriptionInput').value = rule.description || '';
                document.getElementById('riskLevelInput').value = rule.riskLevel || 'MEDIUM';
                document.getElementById('priorityInput').value = rule.priority || 100;
                document.getElementById('enabledInput').value = rule.enabled !== undefined ? rule.enabled : 1;
                
                selectedVulnIds = vulnIds.map(function(id) { return parseInt(id); });
                renderSelectedVulns();
                loadVulnerabilities('');
                
                document.getElementById('ruleModalTitle').textContent = '编辑规则';
                document.getElementById('ruleModal').style.display = 'flex';
            }
        })
        .catch(error => {
            message.error('获取规则详情失败: ' + (error.message || '未知错误'));
        });
}

function editWhitelist(id) {
    http.get('/whitelist/' + id)
        .then(result => {
            if (result) {
                document.getElementById('whitelistId').value = result.id;
                document.getElementById('whitelistTypeInput').value = result.whitelistType || '';
                document.getElementById('whitelistValueInput').value = result.whitelistValue || '';
                document.getElementById('whitelistDescInput').value = result.description || '';
                document.getElementById('whitelistPriorityInput').value = result.priority || 100;
                document.getElementById('whitelistEnabledInput').value = result.enabled !== undefined ? result.enabled : 1;
                
                document.getElementById('whitelistModalTitle').textContent = '编辑白名单';
                document.getElementById('whitelistModal').style.display = 'flex';
            }
        })
        .catch(error => {
            message.error('获取白名单详情失败: ' + (error.message || '未知错误'));
        });
}

function deleteRule(id) {
    if (!confirm('确定要删除该规则吗？此操作不可恢复。')) {
        return;
    }
    
    http.delete('/rule/' + id)
        .then(result => {
            message.success('删除成功');
            ruleTable.refresh();
        })
        .catch(error => {
            message.error('删除失败: ' + (error.message || '未知错误'));
        });
}

function deleteWhitelist(id) {
    if (!confirm('确定要删除该白名单吗？此操作不可恢复。')) {
        return;
    }
    
    http.delete('/whitelist/' + id)
        .then(result => {
            message.success('删除成功');
            whitelistTable.refresh();
        })
        .catch(error => {
            message.error('删除失败: ' + (error.message || '未知错误'));
        });
}

function openAddRuleModal() {
    document.getElementById('ruleId').value = '';
    document.getElementById('ruleNameInput').value = '';
    document.getElementById('attackTypeInput').value = '';
    document.getElementById('ruleExpressionInput').value = '';
    document.getElementById('descriptionInput').value = '';
    document.getElementById('riskLevelInput').value = 'MEDIUM';
    document.getElementById('priorityInput').value = '100';
    document.getElementById('enabledInput').value = '1';
    
    selectedVulnIds = [];
    renderSelectedVulns();
    loadVulnerabilities('');
    
    document.getElementById('ruleModalTitle').textContent = '新增规则';
    document.getElementById('ruleModal').style.display = 'flex';
}

function showAddRuleModal() {
    openAddRuleModal();
}

function closeRuleModal() {
    document.getElementById('ruleModal').style.display = 'none';
}

function showAddWhitelistModal() {
    document.getElementById('whitelistId').value = '';
    document.getElementById('whitelistTypeInput').value = '';
    document.getElementById('whitelistValueInput').value = '';
    document.getElementById('whitelistDescInput').value = '';
    document.getElementById('whitelistPriorityInput').value = '100';
    document.getElementById('whitelistEnabledInput').value = '1';
    
    document.getElementById('whitelistModalTitle').textContent = '新增白名单';
    document.getElementById('whitelistModal').style.display = 'flex';
}

function closeWhitelistModal() {
    document.getElementById('whitelistModal').style.display = 'none';
}

function saveRule() {
    const id = document.getElementById('ruleId').value;
    const ruleName = document.getElementById('ruleNameInput').value.trim();
    const attackType = document.getElementById('attackTypeInput').value.trim();
    const ruleContent = document.getElementById('ruleExpressionInput').value.trim();
    const description = document.getElementById('descriptionInput').value.trim();
    const riskLevel = document.getElementById('riskLevelInput').value;
    const priority = parseInt(document.getElementById('priorityInput').value) || 100;
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
    
    const data = {
        ruleName: ruleName,
        attackType: attackType,
        ruleContent: ruleContent,
        description: description,
        riskLevel: riskLevel,
        priority: priority,
        enabled: enabled,
        vulnerabilityIds: selectedVulnIds
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

function saveWhitelist() {
    const id = document.getElementById('whitelistId').value;
    const whitelistType = document.getElementById('whitelistTypeInput').value.trim();
    const whitelistValue = document.getElementById('whitelistValueInput').value.trim();
    const description = document.getElementById('whitelistDescInput').value.trim();
    const priority = parseInt(document.getElementById('whitelistPriorityInput').value) || 100;
    const enabled = parseInt(document.getElementById('whitelistEnabledInput').value);
    
    if (!whitelistType) {
        message.error('请选择白名单类型');
        return;
    }
    if (!whitelistValue) {
        message.error('请输入白名单值');
        return;
    }
    
    const data = {
        whitelistType: whitelistType,
        whitelistValue: whitelistValue,
        description: description,
        priority: priority,
        enabled: enabled
    };
    
    const isEdit = id && id !== '';
    const url = isEdit ? '/whitelist/update' : '/whitelist/add';
    const method = isEdit ? 'put' : 'post';
    
    if (isEdit) {
        data.id = parseInt(id);
    }
    
    http[method](url, data)
        .then(result => {
            message.success(isEdit ? '更新成功' : '新增成功');
            closeWhitelistModal();
            whitelistTable.refresh();
        })
        .catch(error => {
            message.error((isEdit ? '更新失败' : '新增失败') + ': ' + (error.message || '未知错误'));
        });
}

document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeRuleModal();
        closeWhitelistModal();
    }
});

function toggleSelectAllRules() {
    const selectAll = document.getElementById('selectAllRules');
    const checkboxes = document.querySelectorAll('.rule-checkbox');
    
    checkboxes.forEach(function(cb) {
        cb.checked = selectAll.checked;
    });
    
    updateSelectedRuleIds();
}

function updateSelectedRuleIds() {
    const checkboxes = document.querySelectorAll('.rule-checkbox:checked');
    selectedRuleIds = Array.from(checkboxes).map(function(cb) {
        return parseInt(cb.value);
    });
    
    const selectAll = document.getElementById('selectAllRules');
    const allCheckboxes = document.querySelectorAll('.rule-checkbox');
    if (allCheckboxes.length > 0) {
        selectAll.checked = selectedRuleIds.length === allCheckboxes.length;
    }
}

function toggleSelectAllWhitelists() {
    const selectAll = document.getElementById('selectAllWhitelists');
    const checkboxes = document.querySelectorAll('.whitelist-checkbox');
    
    checkboxes.forEach(function(cb) {
        cb.checked = selectAll.checked;
    });
    
    updateSelectedWhitelistIds();
}

function updateSelectedWhitelistIds() {
    const checkboxes = document.querySelectorAll('.whitelist-checkbox:checked');
    selectedWhitelistIds = Array.from(checkboxes).map(function(cb) {
        return parseInt(cb.value);
    });
    
    const selectAll = document.getElementById('selectAllWhitelists');
    const allCheckboxes = document.querySelectorAll('.whitelist-checkbox');
    if (allCheckboxes.length > 0) {
        selectAll.checked = selectedWhitelistIds.length === allCheckboxes.length;
    }
}

function batchEnableRules() {
    if (selectedRuleIds.length === 0) {
        message.warning('请选择要启用的规则');
        return;
    }
    
    if (!confirm('确定要批量启用选中的 ' + selectedRuleIds.length + ' 条规则吗？')) {
        return;
    }
    
    http.put('/rule/batch-enable', selectedRuleIds)
        .then(function() {
            message.success('批量启用成功');
            clearRuleSelection();
            ruleTable.refresh();
        })
        .catch(function(error) {
            console.error('批量启用失败:', error);
            message.error('批量启用失败');
        });
}

function batchDisableRules() {
    if (selectedRuleIds.length === 0) {
        message.warning('请选择要禁用的规则');
        return;
    }
    
    if (!confirm('确定要批量禁用选中的 ' + selectedRuleIds.length + ' 条规则吗？')) {
        return;
    }
    
    http.put('/rule/batch-disable', selectedRuleIds)
        .then(function() {
            message.success('批量禁用成功');
            clearRuleSelection();
            ruleTable.refresh();
        })
        .catch(function(error) {
            console.error('批量禁用失败:', error);
            message.error('批量禁用失败');
        });
}

function batchDeleteRules() {
    if (selectedRuleIds.length === 0) {
        message.warning('请选择要删除的规则');
        return;
    }
    
    if (!confirm('确定要批量删除选中的 ' + selectedRuleIds.length + ' 条规则吗？此操作不可恢复。')) {
        return;
    }
    
    http.delete('/rule/batch', selectedRuleIds)
        .then(function() {
            message.success('批量删除成功');
            clearRuleSelection();
            ruleTable.refresh();
        })
        .catch(function(error) {
            console.error('批量删除失败:', error);
            message.error('批量删除失败');
        });
}

function batchEnableWhitelists() {
    if (selectedWhitelistIds.length === 0) {
        message.warning('请选择要启用的白名单');
        return;
    }
    
    if (!confirm('确定要批量启用选中的 ' + selectedWhitelistIds.length + ' 条白名单吗？')) {
        return;
    }
    
    http.put('/whitelist/batch-enable', selectedWhitelistIds)
        .then(function() {
            message.success('批量启用成功');
            clearWhitelistSelection();
            whitelistTable.refresh();
        })
        .catch(function(error) {
            console.error('批量启用失败:', error);
            message.error('批量启用失败');
        });
}

function batchDisableWhitelists() {
    if (selectedWhitelistIds.length === 0) {
        message.warning('请选择要禁用的白名单');
        return;
    }
    
    if (!confirm('确定要批量禁用选中的 ' + selectedWhitelistIds.length + ' 条白名单吗？')) {
        return;
    }
    
    http.put('/whitelist/batch-disable', selectedWhitelistIds)
        .then(function() {
            message.success('批量禁用成功');
            clearWhitelistSelection();
            whitelistTable.refresh();
        })
        .catch(function(error) {
            console.error('批量禁用失败:', error);
            message.error('批量禁用失败');
        });
}

function batchDeleteWhitelists() {
    if (selectedWhitelistIds.length === 0) {
        message.warning('请选择要删除的白名单');
        return;
    }
    
    if (!confirm('确定要批量删除选中的 ' + selectedWhitelistIds.length + ' 条白名单吗？此操作不可恢复。')) {
        return;
    }
    
    http.delete('/whitelist/batch', selectedWhitelistIds)
        .then(function() {
            message.success('批量删除成功');
            clearWhitelistSelection();
            whitelistTable.refresh();
        })
        .catch(function(error) {
            console.error('批量删除失败:', error);
            message.error('批量删除失败');
        });
}

function clearRuleSelection() {
    selectedRuleIds = [];
    const selectAll = document.getElementById('selectAllRules');
    if (selectAll) {
        selectAll.checked = false;
    }
    const checkboxes = document.querySelectorAll('.rule-checkbox');
    checkboxes.forEach(function(cb) {
        cb.checked = false;
    });
}

function clearWhitelistSelection() {
    selectedWhitelistIds = [];
    const selectAll = document.getElementById('selectAllWhitelists');
    if (selectAll) {
        selectAll.checked = false;
    }
    const checkboxes = document.querySelectorAll('.whitelist-checkbox');
    checkboxes.forEach(function(cb) {
        cb.checked = false;
    });
}

function initVulnSelectEvents() {
    const searchInput = document.getElementById('vulnSearchInput');
    if (searchInput) {
        searchInput.addEventListener('input', function(e) {
            const keyword = e.target.value.trim();
            if (vulnSearchTimeout) {
                clearTimeout(vulnSearchTimeout);
            }
            vulnSearchTimeout = setTimeout(function() {
                loadVulnerabilities(keyword);
            }, 300);
        });
        
        searchInput.addEventListener('focus', function() {
            showVulnDropdown();
        });
    }
    
    document.addEventListener('click', function(e) {
        const container = document.getElementById('vulnSelectContainer');
        if (container && !container.contains(e.target)) {
            hideVulnDropdown();
        }
    });
}

function toggleVulnDropdown() {
    const dropdown = document.getElementById('vulnDropdown');
    if (dropdown.style.display === 'none') {
        showVulnDropdown();
    } else {
        hideVulnDropdown();
    }
}

function showVulnDropdown() {
    const dropdown = document.getElementById('vulnDropdown');
    dropdown.style.display = 'block';
}

function hideVulnDropdown() {
    const dropdown = document.getElementById('vulnDropdown');
    dropdown.style.display = 'none';
}

function loadVulnerabilities(keyword) {
    const url = keyword ? '/rule/available-vulnerabilities?keyword=' + encodeURIComponent(keyword) : '/rule/available-vulnerabilities';
    
    http.get(url)
        .then(function(result) {
            allVulnerabilities = result || [];
            renderVulnDropdown();
        })
        .catch(function(error) {
            console.error('加载漏洞列表失败:', error);
            allVulnerabilities = [];
            renderVulnDropdown();
        });
}

function renderVulnDropdown() {
    const listEl = document.getElementById('vulnSelectList');
    
    if (allVulnerabilities.length === 0) {
        listEl.innerHTML = '<div class="vuln-empty">暂无可关联的漏洞</div>';
        return;
    }
    
    let html = '';
    allVulnerabilities.forEach(function(vuln) {
        const isSelected = selectedVulnIds.indexOf(vuln.id) !== -1;
        const levelClass = 'vuln-level-' + (vuln.vulnLevel || 'MEDIUM');
        
        html += '<div class="vuln-select-item' + (isSelected ? ' selected' : '') + '" onclick="toggleVulnSelect(' + vuln.id + ')">';
        html += '<div class="vuln-select-item-header">';
        html += '<span class="vuln-select-item-id">ID: ' + vuln.id + '</span>';
        html += '<span class="vuln-select-item-level ' + levelClass + '">' + formatVulnLevel(vuln.vulnLevel) + '</span>';
        html += '</div>';
        html += '<div class="vuln-select-item-name">' + escapeHtml(vuln.vulnName || '') + '</div>';
        html += '<div class="vuln-select-item-info">' + escapeHtml(vuln.vulnType || '') + ' | ' + escapeHtml(vuln.vulnPath || '') + '</div>';
        html += '</div>';
    });
    
    listEl.innerHTML = html;
}

function toggleVulnSelect(vulnId) {
    const index = selectedVulnIds.indexOf(vulnId);
    if (index === -1) {
        selectedVulnIds.push(vulnId);
    } else {
        selectedVulnIds.splice(index, 1);
    }
    renderSelectedVulns();
    renderVulnDropdown();
}

function renderSelectedVulns() {
    const container = document.getElementById('selectedVulns');
    
    if (selectedVulnIds.length === 0) {
        container.innerHTML = '';
        return;
    }
    
    let html = '';
    selectedVulnIds.forEach(function(vulnId) {
        const vuln = allVulnerabilities.find(function(v) { return v.id === vulnId; });
        const displayName = vuln ? ('ID:' + vulnId + ' ' + (vuln.vulnName || '')) : ('ID:' + vulnId);
        
        html += '<span class="selected-vuln-tag">';
        html += escapeHtml(displayName);
        html += '<span class="remove-btn" onclick="removeVulnSelect(' + vulnId + ')">×</span>';
        html += '</span>';
    });
    
    container.innerHTML = html;
}

function removeVulnSelect(vulnId) {
    const index = selectedVulnIds.indexOf(vulnId);
    if (index !== -1) {
        selectedVulnIds.splice(index, 1);
        renderSelectedVulns();
        renderVulnDropdown();
    }
}

function formatVulnLevel(level) {
    const levelMap = {
        'CRITICAL': '严重',
        'HIGH': '高危',
        'MEDIUM': '中危',
        'LOW': '低危'
    };
    return levelMap[level] || level || '中危';
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
