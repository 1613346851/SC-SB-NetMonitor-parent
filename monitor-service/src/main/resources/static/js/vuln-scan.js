let scanPageState = {
    pollingTimer: null,
    currentResults: [],
    historyList: [],
    resultSortField: 'detectedAt',
    resultSortOrder: 'desc',
    historySortField: 'startTime',
    historySortOrder: 'desc'
};

document.addEventListener('DOMContentLoaded', function() {
    bindScanModeUI();
    bindDetailModal();
    initTableSorting();
    loadScanState();
    loadScanInterfaces();
    loadScanConfigList();
});

function bindScanModeUI() {
    document.querySelectorAll('input[name="scanType"]').forEach(radio => {
        radio.addEventListener('change', () => {
            document.querySelectorAll('.scan-mode-option').forEach(item => item.classList.remove('active'));
            radio.closest('.scan-mode-option')?.classList.add('active');
            loadScanInterfaces();
        });
    });
}

function bindDetailModal() {
    const modal = document.getElementById('scanResultDetailModal');
    modal?.addEventListener('click', function(event) {
        if (event.target === this) {
            closeScanResultDetail();
        }
    });
}

function initTableSorting() {
    initResultTableSorting();
    initHistoryTableSorting();
}

function initResultTableSorting() {
    const table = document.querySelector('#scanResultTableBody')?.closest('table');
    if (!table) return;
    
    const headers = table.querySelectorAll('th[data-sort]');
    headers.forEach(header => {
        header.classList.add('sortable');
        if (!header.querySelector('.sort-icon')) {
            header.innerHTML += `<span class="sort-icon"><span class="up">▲</span><span class="down">▼</span></span>`;
        }
        
        header.addEventListener('click', (e) => {
            const field = header.dataset.sort;
            if (scanPageState.resultSortField === field) {
                scanPageState.resultSortOrder = scanPageState.resultSortOrder === 'asc' ? 'desc' : 'asc';
            } else {
                scanPageState.resultSortField = field;
                scanPageState.resultSortOrder = 'asc';
            }
            updateSortIcons(table, scanPageState.resultSortField, scanPageState.resultSortOrder);
            renderResultTable(scanPageState.currentResults);
        });
    });
}

function initHistoryTableSorting() {
    const table = document.querySelector('#scanHistoryTableBody')?.closest('table');
    if (!table) return;
    
    const headers = table.querySelectorAll('th[data-sort]');
    headers.forEach(header => {
        header.classList.add('sortable');
        if (!header.querySelector('.sort-icon')) {
            header.innerHTML += `<span class="sort-icon"><span class="up">▲</span><span class="down">▼</span></span>`;
        }
        
        header.addEventListener('click', (e) => {
            const field = header.dataset.sort;
            if (scanPageState.historySortField === field) {
                scanPageState.historySortOrder = scanPageState.historySortOrder === 'asc' ? 'desc' : 'asc';
            } else {
                scanPageState.historySortField = field;
                scanPageState.historySortOrder = 'asc';
            }
            updateSortIcons(table, scanPageState.historySortField, scanPageState.historySortOrder);
            renderHistoryTable(scanPageState.historyList);
        });
    });
}

function updateSortIcons(table, sortField, sortOrder) {
    const headers = table.querySelectorAll('th[data-sort]');
    headers.forEach(header => {
        const icon = header.querySelector('.sort-icon');
        if (icon) {
            icon.className = 'sort-icon';
            if (header.dataset.sort === sortField) {
                icon.classList.add(sortOrder);
            }
        }
    });
}

async function loadScanState() {
    try {
        const [progress, result] = await Promise.all([
            http.get('/vuln/scan/progress'),
            http.get('/vuln/scan/result')
        ]);
        renderProgress(progress || {});
        renderScanResult(result || {});
        togglePolling((progress || {}).status);
    } catch (error) {
        console.error('加载扫描状态失败:', error);
        message.error('加载扫描状态失败：' + (error.message || '未知错误'));
    }
}

async function loadScanInterfaces() {
    try {
        const scanType = document.querySelector('input[name="scanType"]:checked')?.value || 'QUICK';
        const result = await http.get(`/vuln/scan/interfaces?scanType=${scanType}`);
        if (result && result.interfaces) {
            renderInterfaceTags(result.interfaces);
        }
    } catch (error) {
        console.error('加载扫描接口列表失败:', error);
    }
}

async function loadScanConfigList() {
    try {
        const result = await http.get('/scan-interface/all-with-relations');
        console.log('扫描配置列表返回:', result);
        if (result && Array.isArray(result)) {
            renderScanConfigTable(result);
        } else {
            console.error('扫描配置列表格式错误:', result);
            const tbody = document.getElementById('scanConfigTableBody');
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="9" class="text-center">数据格式错误</td></tr>';
            }
        }
    } catch (error) {
        console.error('加载扫描配置列表失败:', error);
        const tbody = document.getElementById('scanConfigTableBody');
        if (tbody) {
            let errorMsg = '加载失败';
            if (error.message && error.message.includes('401')) {
                errorMsg = '未授权，请重新登录';
            } else if (error.message && error.message.includes('404')) {
                errorMsg = '接口不存在，请检查服务是否启动';
            } else if (error.message && error.message.includes('500')) {
                errorMsg = '服务器错误，请检查数据库是否初始化';
            } else {
                errorMsg = `加载失败: ${error.message || '未知错误'}`;
            }
            tbody.innerHTML = `<tr><td colspan="9" class="text-center">${errorMsg}<br><small>提示：请确保已执行数据库初始化脚本 init_all.sql</small></td></tr>`;
        }
    }
}

function renderScanConfigTable(list) {
    const tbody = document.getElementById('scanConfigTableBody');
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center">暂无配置</td></tr>';
        return;
    }

    tbody.innerHTML = list.map(item => {
        let defenseStatusHtml = '';
        const status = item.defenseRuleStatus || 0;
        if (status === 2) {
            defenseStatusHtml = '<span class="tag success">已配置</span>';
        } else if (status === 1) {
            defenseStatusHtml = '<span class="tag warning">部分已配置</span>';
        } else {
            defenseStatusHtml = '<span class="tag info">未配置</span>';
        }
        
        return `
        <tr>
            <td>${CellRenderer.renderText(item.interfaceName)}</td>
            <td><code>${CellRenderer.renderText(item.interfacePath)}</code></td>
            <td>${CellRenderer.renderText(item.vulnType)}</td>
            <td><span class="badge badge-info">${item.ruleCount || 0}</span></td>
            <td><span class="badge badge-warning">${item.vulnCount || 0}</span></td>
            <td><span class="badge badge-success">${item.verifiedCount || 0}</span></td>
            <td>${defenseStatusHtml}</td>
            <td>${item.enabled === 1 ? '<span class="tag success">已启用</span>' : '<span class="tag warning">已禁用</span>'}</td>
            <td>
                <button class="btn btn-sm btn-info" onclick="showInterfaceDetail(${item.interfaceId})">详情</button>
                <button class="btn btn-sm ${item.enabled === 1 ? 'btn-warning' : 'btn-success'}" 
                        onclick="toggleScanInterface(${item.interfaceId}, ${item.enabled || 0})">
                    ${item.enabled === 1 ? '禁用' : '启用'}
                </button>
                <button class="btn btn-sm ${status === 2 ? 'btn-warning' : 'btn-success'}" 
                        onclick="toggleDefenseRule(${item.interfaceId}, ${status})">
                    ${status === 2 ? '取消防御' : '标记防御'}
                </button>
                <button class="btn btn-sm btn-danger" onclick="deleteScanInterface(${item.interfaceId})">删除</button>
            </td>
        </tr>
    `}).join('');
}

async function toggleScanInterface(id, currentEnabled) {
    try {
        const newEnabled = currentEnabled === 1 ? 0 : 1;
        await http.put(`/scan-interface/${id}/enabled?enabled=${newEnabled}`);
        message.success(newEnabled === 1 ? '接口已启用' : '接口已禁用');
        loadScanConfigList();
        loadScanInterfaces();
    } catch (error) {
        console.error('切换接口状态失败:', error);
        message.error('操作失败：' + (error.message || '未知错误'));
    }
}

async function toggleDefenseRule(id, currentStatus) {
    try {
        const newStatus = currentStatus === 2 ? 0 : 2;
        const note = newStatus === 2 ? '手动标记：已配置防御规则' : '';
        
        await http.put(`/scan-interface/${id}/defense-rule?hasDefenseRule=${newStatus}&defenseRuleNote=${encodeURIComponent(note)}`);
        message.success(newStatus === 2 ? '已标记为已配置防御规则' : '已取消防御标记');
        loadScanConfigList();
    } catch (error) {
        console.error('切换防御规则标记失败:', error);
        message.error('操作失败：' + (error.message || '未知错误'));
    }
}

function showAddInterfaceModal() {
    document.getElementById('addInterfaceModal').style.display = 'flex';
    document.getElementById('addInterfaceForm').reset();
}

function closeAddInterfaceModal() {
    document.getElementById('addInterfaceModal').style.display = 'none';
}

async function submitAddInterface() {
    try {
        const interfaceName = document.getElementById('interfaceName').value.trim();
        const interfacePath = document.getElementById('interfacePath').value.trim();
        const httpMethod = document.getElementById('httpMethod').value;
        const vulnType = document.getElementById('vulnType').value;
        const riskLevel = document.getElementById('riskLevel').value;
        const priority = parseInt(document.getElementById('priority').value) || 100;

        if (!interfaceName || !interfacePath) {
            message.error('请填写必填项');
            return;
        }

        const entity = {
            targetId: 1,
            interfaceName,
            interfacePath,
            httpMethod,
            vulnType,
            riskLevel,
            priority,
            enabled: 1,
            defenseRuleStatus: 0,
            defenseRuleCount: 0,
            paramsConfig: '{}',
            payloadConfig: '{}',
            matchRules: '{}'
        };

        await http.post('/scan-interface/add', entity);
        message.success('接口添加成功');
        closeAddInterfaceModal();
        loadScanConfigList();
        loadScanInterfaces();
    } catch (error) {
        console.error('添加接口失败:', error);
        message.error('添加失败：' + (error.message || '未知错误'));
    }
}

async function deleteScanInterface(id) {
    if (!confirm('确定要删除这个接口吗？')) {
        return;
    }
    
    try {
        await http.delete(`/scan-interface/${id}`);
        message.success('接口删除成功');
        loadScanConfigList();
        loadScanInterfaces();
    } catch (error) {
        console.error('删除接口失败:', error);
        message.error('删除失败：' + (error.message || '未知错误'));
    }
}

async function showInterfaceDetail(id) {
    try {
        const modal = document.getElementById('interfaceDetailModal');
        modal.style.display = 'flex';
        
        const result = await http.get(`/scan-interface/${id}/detail`);
        
        renderInterfaceBasicInfo(result.interface);
        renderRelatedVulnTable(result.vulnerabilities || []);
        renderRelatedRuleTable(result.rules || []);
    } catch (error) {
        console.error('加载接口详情失败:', error);
        message.error('加载详情失败：' + (error.message || '未知错误'));
    }
}

function closeInterfaceDetailModal() {
    document.getElementById('interfaceDetailModal').style.display = 'none';
}

function renderInterfaceBasicInfo(interfaceEntity) {
    const container = document.getElementById('interfaceBasicInfo');
    if (!container || !interfaceEntity) {
        container.innerHTML = '<p class="text-muted">暂无信息</p>';
        return;
    }

    const status = interfaceEntity.defenseRuleStatus || 0;
    let defenseStatusText = '未配置';
    if (status === 2) {
        defenseStatusText = '已配置';
    } else if (status === 1) {
        defenseStatusText = '部分已配置';
    }

    const info = [
        { label: '接口名称', value: interfaceEntity.interfaceName },
        { label: '接口路径', value: interfaceEntity.interfacePath },
        { label: 'HTTP方法', value: interfaceEntity.httpMethod },
        { label: '漏洞类型', value: interfaceEntity.vulnType },
        { label: '风险等级', value: interfaceEntity.riskLevel },
        { label: '扫描优先级', value: interfaceEntity.priority },
        { label: '防御规则', value: defenseStatusText },
        { label: '关联规则数', value: interfaceEntity.defenseRuleCount || 0 },
        { label: '创建时间', value: interfaceEntity.createTime || '-' }
    ];

    container.innerHTML = info.map(item => `
        <div class="detail-info-item">
            <span class="detail-info-label">${item.label}：</span>
            <span class="detail-info-value">${CellRenderer.renderText(item.value)}</span>
        </div>
    `).join('');
}

function renderRelatedVulnTable(vulnList) {
    const tbody = document.getElementById('relatedVulnTableBody');
    if (!tbody) return;

    if (!vulnList || vulnList.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">暂无关联漏洞记录</td></tr>';
        return;
    }

    tbody.innerHTML = vulnList.map(item => `
        <tr>
            <td>${CellRenderer.renderText(item.vulnName)}</td>
            <td>${CellRenderer.renderText(item.vulnType)}</td>
            <td>${CellRenderer.renderRiskLevel(item.vulnLevel || 'LOW')}</td>
            <td>${CellRenderer.renderVerifyStatus(item.verifyStatus || 0)}</td>
            <td>${CellRenderer.renderText(item.createTime || '-')}</td>
        </tr>
    `).join('');
}

function renderRelatedRuleTable(ruleList) {
    const tbody = document.getElementById('relatedRuleTableBody');
    if (!tbody) return;

    if (!ruleList || ruleList.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">暂无关联防御规则</td></tr>';
        return;
    }

    tbody.innerHTML = ruleList.map(item => `
        <tr>
            <td>${CellRenderer.renderText(item.ruleName)}</td>
            <td>${CellRenderer.renderText(item.attackType)}</td>
            <td>${CellRenderer.renderRiskLevel(item.riskLevel || 'LOW')}</td>
            <td>${item.enabled === 1 ? '<span class="tag success">已启用</span>' : '<span class="tag warning">已禁用</span>'}</td>
            <td>${item.priority || '-'}</td>
        </tr>
    `).join('');
}

async function startScan() {
    const scanType = document.querySelector('input[name="scanType"]:checked')?.value || 'QUICK';
    try {
        const result = await http.post('/vuln/scan/start', { scanType });
        message.success(result.message || '扫描任务已启动');
        renderProgress(result);
        renderScanResult(result);
        togglePolling(result.status);
    } catch (error) {
        console.error('启动扫描失败:', error);
        message.error('启动扫描失败：' + (error.message || '未知错误'));
    }
}

async function controlScan(action) {
    try {
        const result = await http.post('/vuln/scan/control', { action });
        message.success(result.message || '操作成功');
        renderProgress(result);
        if (action === 'TERMINATE') {
            const latest = await http.get('/vuln/scan/result');
            renderScanResult(latest || result);
            togglePolling((latest || result).status);
            return;
        }
        togglePolling(result.status);
    } catch (error) {
        console.error('控制扫描失败:', error);
        message.error('控制扫描失败：' + (error.message || '未知错误'));
    }
}

async function manualSync() {
    try {
        const result = await http.post('/vuln/scan/sync');
        message.success(result.message || '同步完成');
        renderScanResult(result || {});
        renderProgress(result || {});
    } catch (error) {
        console.error('同步扫描结果失败:', error);
        message.error('同步扫描结果失败：' + (error.message || '未知错误'));
    }
}

function renderProgress(progress) {
    const status = progress.status || 'IDLE';
    const percent = Number(progress.progressPercent || 0);
    const completed = Number(progress.completedInterfaces || 0);
    const total = Number(progress.totalInterfaces || 0);
    const discovered = Number(progress.discoveredCount || 0);

    setText('scanStatusText', getStatusLabel(status));
    setText('scanLastMessage', progress.message || progress.lastMessage || '等待启动');
    const targetInput = document.getElementById('scanTarget');
    if (targetInput && progress.target) {
        targetInput.value = progress.target.replace('http://', '').replace('https://', '');
    }
    if (progress.scanType) {
        const radio = document.querySelector(`input[name="scanType"][value="${progress.scanType}"]`);
        if (radio) {
            radio.checked = true;
            document.querySelectorAll('.scan-mode-option').forEach(item => item.classList.remove('active'));
            radio.closest('.scan-mode-option')?.classList.add('active');
        }
    }


    setText('progressPercentValue', `${percent}%`);
    setText('progressCountValue', `${completed} / ${total}`);
    setText('discoveredCountValue', discovered);
    setText('scanStartTimeValue', progress.startTime || '--');
    setText('scanEndTimeValue', progress.endTime || '--');
    setText('scanDurationValue', formatDuration(progress.durationSeconds || 0));
    setText('scanCurrentStep', progress.currentStep || '等待启动');
    setText('scanProgressText', `${percent}%`);
    setText('scanSummaryText', progress.summary || progress.message || '等待启动');
    setText('syncedCountValue', scanPageState.currentResults.filter(item => item.synced).length);

    const statusEl = document.getElementById('scanStatusText');
    if (statusEl) {
        statusEl.className = `scan-status-card__value ${getStatusClass(status)}`;
    }

    const progressBar = document.getElementById('scanProgressBar');
    if (progressBar) {
        progressBar.style.width = `${percent}%`;
    }

    renderWarnings(progress.warnings || []);
    renderInterfaceTags(progress.interfaces || []);
    updateButtonStates(status, scanPageState.currentResults);
}

function renderScanResult(result) {
    const results = Array.isArray(result.results) ? result.results : [];
    const historyList = Array.isArray(result.history) ? result.history : [];
    scanPageState.currentResults = results;
    scanPageState.historyList = historyList;

    renderResultTable(results);
    renderHistoryTable(historyList);
    setText('syncedCountValue', results.filter(item => item.synced).length);
    updateButtonStates(result.status || 'IDLE', results);
}

function sortResults(results) {
    const field = scanPageState.resultSortField;
    const order = scanPageState.resultSortOrder;
    
    return [...results].sort((a, b) => {
        let valueA = a[field];
        let valueB = b[field];
        
        if (valueA === null || valueA === undefined) valueA = '';
        if (valueB === null || valueB === undefined) valueB = '';
        
        if (field === 'detectedAt' || field === 'createTime') {
            valueA = valueA ? new Date(valueA).getTime() : 0;
            valueB = valueB ? new Date(valueB).getTime() : 0;
        } else if (field === 'vulnLevel') {
            const levelOrder = { 'CRITICAL': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };
            valueA = levelOrder[valueA] || 0;
            valueB = levelOrder[valueB] || 0;
        } else if (typeof valueA === 'string') {
            valueA = valueA.toLowerCase();
            valueB = valueB.toLowerCase();
        }
        
        let result = 0;
        if (valueA < valueB) result = -1;
        else if (valueA > valueB) result = 1;
        
        return order === 'asc' ? result : -result;
    });
}

function sortHistory(historyList) {
    const field = scanPageState.historySortField;
    const order = scanPageState.historySortOrder;
    
    return [...historyList].sort((a, b) => {
        let valueA = a[field];
        let valueB = b[field];
        
        if (valueA === null || valueA === undefined) valueA = '';
        if (valueB === null || valueB === undefined) valueB = '';
        
        if (field === 'startTime' || field === 'endTime') {
            valueA = valueA ? new Date(valueA).getTime() : 0;
            valueB = valueB ? new Date(valueB).getTime() : 0;
        } else if (field === 'status') {
            const statusOrder = { 'RUNNING': 4, 'PAUSED': 3, 'COMPLETED': 2, 'TERMINATED': 1, 'FAILED': 0 };
            valueA = statusOrder[valueA] || 0;
            valueB = statusOrder[valueB] || 0;
        } else if (typeof valueA === 'string') {
            valueA = valueA.toLowerCase();
            valueB = valueB.toLowerCase();
        }
        
        let result = 0;
        if (valueA < valueB) result = -1;
        else if (valueA > valueB) result = 1;
        
        return order === 'asc' ? result : -result;
    });
}

function renderResultTable(results) {
    const tbody = document.getElementById('scanResultTableBody');
    if (!tbody) return;

    if (!results.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">暂无结果</td></tr>';
        return;
    }

    const sortedResults = sortResults(results);
    const cell = TableUtils.cell;
    cell._currentTableBodyId = 'scanResultTableBody';

    tbody.innerHTML = sortedResults.map((item, index) => `
        <tr>
            ${cell.renderCell(item.vulnName, { maxLength: 20 })}
            <td>${cell.renderAttackType(item.vulnType || 'UNKNOWN')}</td>
            <td>${cell.renderRiskLevel(item.vulnLevel || 'LOW')}</td>
            ${cell.renderCell(item.vulnPath, { maxLength: 30 })}
            ${cell.renderCell(item.payload, { maxLength: 30 })}
            <td>${item.synced ? '<span class="tag success">已同步</span>' : '<span class="tag warning">待同步</span>'}</td>
            <td>${item.detectedAt || '-'}</td>
            ${cell.renderActionCell([
                { text: '详情', type: 'primary', onClick: `viewScanResultDetail(${scanPageState.currentResults.indexOf(item)})` }
            ])}
        </tr>
    `).join('');
    
    cell._currentTableBodyId = null;
    TableUtils.bindTooltip(tbody);
}

function renderHistoryTable(historyList) {
    const tbody = document.getElementById('scanHistoryTableBody');
    if (!tbody) return;

    if (!historyList.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">暂无历史记录</td></tr>';
        return;
    }

    const sortedHistory = sortHistory(historyList);
    const cell = TableUtils.cell;

    tbody.innerHTML = sortedHistory.map(item => `
        <tr>
            <td><code>${cell.renderText(item.taskId)}</code></td>
            <td>${cell.renderText(item.scanType)}</td>
            <td><span class="tag ${getStatusTagClass(item.status)}">${getStatusLabel(item.status)}</span></td>
            <td>${item.discoveredCount || 0}</td>
            <td>${item.completedInterfaces || 0} / ${item.totalInterfaces || 0}</td>
            <td>${cell.renderText(item.startTime)}</td>
            <td>${cell.renderText(item.endTime)}</td>
            ${cell.renderCell(item.summary, { maxLength: 36, showTooltip: true })}
        </tr>
    `).join('');
    
    TableUtils.bindTooltip(tbody);
}

function renderWarnings(warnings) {
    const container = document.getElementById('scanWarnings');
    if (!container) return;

    if (!warnings.length) {
        container.innerHTML = '';
        return;
    }

    container.innerHTML = warnings.map(item => `<div class="scan-warning-item">${CellRenderer.renderText(item)}</div>`).join('');
}

function renderInterfaceTags(interfaces) {
    const container = document.getElementById('interfaceTagList');
    if (!container) return;

    if (!interfaces.length) {
        container.innerHTML = '<span class="tag info">暂无扫描计划</span>';
        return;
    }

    container.innerHTML = interfaces.map(item => `
        <span class="interface-tag" title="${CellRenderer.escapeAttr(item.title || '')}">
            <strong>${CellRenderer.renderText(item.method || 'GET')}</strong>
            <span>${CellRenderer.renderText(item.path)}</span>
        </span>
    `).join('');
}

function updateButtonStates(status, results) {
    const hasResults = Array.isArray(results) && results.length > 0;
    const isRunning = status === 'RUNNING';
    const isPaused = status === 'PAUSED';

    setDisabled('startScanBtn', isRunning || isPaused);
    setDisabled('pauseScanBtn', !isRunning);
    setDisabled('resumeScanBtn', !isPaused);
    setDisabled('terminateScanBtn', !(isRunning || isPaused));
    setDisabled('syncScanBtn', !hasResults);
}

function togglePolling(status) {
    const active = status === 'RUNNING' || status === 'PAUSED';
    if (scanPageState.pollingTimer) {
        clearInterval(scanPageState.pollingTimer);
        scanPageState.pollingTimer = null;
    }

    if (active) {
        scanPageState.pollingTimer = setInterval(async () => {
            try {
                const [progress, result] = await Promise.all([
                    http.get('/vuln/scan/progress'),
                    http.get('/vuln/scan/result')
                ]);
                renderProgress(progress || {});
                renderScanResult(result || {});
                if (!['RUNNING', 'PAUSED'].includes((progress || {}).status)) {
                    togglePolling((progress || {}).status);
                }
            } catch (error) {
                console.error('轮询扫描进度失败:', error);
                togglePolling('IDLE');
            }
        }, 1500);
    }
}

function viewScanResultDetail(index) {
    const result = scanPageState.currentResults[index];
    if (!result) {
        message.error('扫描结果不存在');
        return;
    }

    const content = document.getElementById('scanResultDetailContent');
    if (!content) return;

    content.innerHTML = `
        <div class="scan-detail-grid">
            <div>
                <p><strong>漏洞名称：</strong>${CellRenderer.renderText(result.vulnName)}</p>
                <p><strong>漏洞类型：</strong>${CellRenderer.renderText(result.vulnType)}</p>
                <p><strong>风险等级：</strong>${TableRenderer.renderRiskLevel(result.vulnLevel || 'LOW')}</p>
                <p><strong>请求方法：</strong>${CellRenderer.renderText(result.requestMethod)}</p>
                <p><strong>漏洞路径：</strong><code>${CellRenderer.renderText(result.vulnPath)}</code></p>
                <p><strong>匹配关键字：</strong>${CellRenderer.renderText(result.matchedKeyword)}</p>
            </div>
            <div>
                <p><strong>发现时间：</strong>${CellRenderer.renderText(result.detectedAt)}</p>
                <p><strong>来源：</strong>${CellRenderer.renderText(result.source)}</p>
                <p><strong>同步状态：</strong>${result.synced ? '<span class="tag success">已同步</span>' : '<span class="tag warning">待同步</span>'}</p>
                <p><strong>漏洞ID：</strong>${CellRenderer.renderText(result.vulnerabilityId ? String(result.vulnerabilityId) : null)}</p>
            </div>
        </div>
        <div class="scan-detail-section">
            <p><strong>Payload：</strong></p>
            <pre class="scan-code-block">${CellRenderer.renderText(result.payload)}</pre>
        </div>
        <div class="scan-detail-section">
            <p><strong>漏洞描述：</strong></p>
            <p>${CellRenderer.renderText(result.description)}</p>
        </div>
        <div class="scan-detail-section">
            <p><strong>修复建议：</strong></p>
            <p>${CellRenderer.renderText(result.fixSuggestion)}</p>
        </div>
        <div class="scan-detail-section">
            <p><strong>响应摘要：</strong></p>
            <pre class="scan-code-block">${CellRenderer.renderText(result.responseSnippet)}</pre>
        </div>
    `;

    document.getElementById('scanResultDetailModal').style.display = 'flex';
}

function closeScanResultDetail() {
    const modal = document.getElementById('scanResultDetailModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) {
        el.textContent = value;
    }
}

function setDisabled(id, disabled) {
    const el = document.getElementById(id);
    if (el) {
        el.disabled = !!disabled;
    }
}

function getStatusLabel(status) {
    const statusMap = {
        IDLE: '空闲',
        RUNNING: '运行中',
        PAUSED: '已暂停',
        COMPLETED: '已完成',
        TERMINATED: '已终止',
        FAILED: '失败'
    };
    return statusMap[status] || '未知';
}

function getStatusClass(status) {
    const classMap = {
        IDLE: 'status-idle',
        RUNNING: 'status-running',
        PAUSED: 'status-paused',
        COMPLETED: 'status-completed',
        TERMINATED: 'status-terminated',
        FAILED: 'status-failed'
    };
    return classMap[status] || 'status-idle';
}

function getStatusTagClass(status) {
    const classMap = {
        RUNNING: 'info',
        PAUSED: 'warning',
        COMPLETED: 'success',
        TERMINATED: 'danger',
        FAILED: 'danger',
        IDLE: 'info'
    };
    return classMap[status] || 'info';
}

function formatDuration(seconds) {
    const totalSeconds = Number(seconds || 0);
    if (totalSeconds < 60) {
        return `${totalSeconds}s`;
    }
    const minutes = Math.floor(totalSeconds / 60);
    const remain = totalSeconds % 60;
    return `${minutes}m ${remain}s`;
}
