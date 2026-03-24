let configList = [];
let filteredConfigList = [];
let currentFilterMode = 'ALL';
let sortField = 'id';
let sortOrder = 'desc';

const AI_CONFIG_KEYS = ['ai.model.url', 'ai.model.apiKey'];
const GATEWAY_CONFIG_PREFIX = 'gateway.';
const GATEWAY_DEFENSE_KEYS = [
    'gateway.defense.blacklist.enabled',
    'gateway.defense.rate-limit.enabled',
    'gateway.defense.malicious-request.enabled'
];

document.addEventListener('DOMContentLoaded', function() {
    bindConfigModal();
    initTableSorting();
    loadConfigList();
    checkGatewayStatus();
});

function bindConfigModal() {
    const modal = document.getElementById('configModal');
    modal?.addEventListener('click', function(event) {
        if (event.target === this) {
            closeConfigModal();
        }
    });
    
    const gatewayModal = document.getElementById('gatewayDefenseModal');
    gatewayModal?.addEventListener('click', function(event) {
        if (event.target === this) {
            closeGatewayDefenseModal();
        }
    });
}

function initTableSorting() {
    const table = document.querySelector('.data-table');
    if (!table) return;
    
    const headers = table.querySelectorAll('th[data-sort]');
    headers.forEach(header => {
        header.classList.add('sortable');
        if (!header.querySelector('.sort-icon')) {
            header.innerHTML += `<span class="sort-icon"><span class="up">▲</span><span class="down">▼</span></span>`;
        }
        
        header.addEventListener('click', (e) => {
            if (e.target.classList.contains('th-resizer')) return;
            const field = header.dataset.sort;
            if (sortField === field) {
                sortOrder = sortOrder === 'asc' ? 'desc' : 'asc';
            } else {
                sortField = field;
                sortOrder = 'asc';
            }
            updateSortIcons(table);
            applyFilters();
        });
    });
    
    initColumnResizer(table);
    updateSortIcons(table);
}

function initColumnResizer(table) {
    const cols = table.querySelectorAll('th');
    cols.forEach(th => {
        if (th.querySelector('.th-resizer')) return;
        const resizer = document.createElement('div');
        resizer.classList.add('th-resizer');
        th.appendChild(resizer);
        
        let startX, startWidth;
        
        const onMouseMove = (e) => {
            const newWidth = startWidth + (e.pageX - startX);
            if (newWidth >= 60) {
                th.style.width = `${newWidth}px`;
                th.style.minWidth = `${newWidth}px`;
            }
        };
        
        const onMouseUp = () => {
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
            resizer.classList.remove('th-resizing');
        };
        
        resizer.addEventListener('mousedown', (e) => {
            e.preventDefault();
            e.stopPropagation();
            startX = e.pageX;
            startWidth = th.offsetWidth;
            resizer.classList.add('th-resizing');
            document.addEventListener('mousemove', onMouseMove);
            document.addEventListener('mouseup', onMouseUp);
        });
    });
}

function updateSortIcons(table) {
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

async function loadConfigList() {
    try {
        configList = await http.get('/config/list');
        applyFilters();
        updateGatewayDefenseDisplay();
    } catch (error) {
        console.error('加载配置列表失败:', error);
        configList = [];
        filteredConfigList = [];
        renderConfigTable([]);
        renderOverview([]);
        message.error('加载配置列表失败：' + (error.message || '未知错误'));
    }
}

function applyFilters() {
    const keyword = document.getElementById('configKey')?.value.trim().toLowerCase() || '';
    const category = document.getElementById('configCategory')?.value || 'ALL';
    let list = [...configList];

    if (currentFilterMode === 'AI_ONLY') {
        list = list.filter(item => AI_CONFIG_KEYS.includes(item.configKey));
    } else if (currentFilterMode === 'GATEWAY_ONLY') {
        list = list.filter(item => item.configKey && item.configKey.startsWith(GATEWAY_CONFIG_PREFIX));
    }

    if (category === 'GATEWAY') {
        list = list.filter(item => item.configKey && item.configKey.startsWith(GATEWAY_CONFIG_PREFIX));
    } else if (category === 'AI') {
        list = list.filter(item => AI_CONFIG_KEYS.includes(item.configKey));
    } else if (category === 'MONITOR') {
        list = list.filter(item => 
            !AI_CONFIG_KEYS.includes(item.configKey) && 
            !(item.configKey && item.configKey.startsWith(GATEWAY_CONFIG_PREFIX))
        );
    }

    if (keyword) {
        list = list.filter(item => {
            const key = (item.configKey || '').toLowerCase();
            const desc = (item.description || '').toLowerCase();
            return key.includes(keyword) || desc.includes(keyword);
        });
    }

    list = sortConfigList(list);

    filteredConfigList = list;

    renderOverview(filteredConfigList);
    renderConfigTable(filteredConfigList);
}

function sortConfigList(list) {
    return list.sort((a, b) => {
        const aGateway = a.configKey && a.configKey.startsWith(GATEWAY_CONFIG_PREFIX);
        const bGateway = b.configKey && b.configKey.startsWith(GATEWAY_CONFIG_PREFIX);
        const aAi = AI_CONFIG_KEYS.includes(a.configKey);
        const bAi = AI_CONFIG_KEYS.includes(b.configKey);
        
        if (aGateway !== bGateway) {
            return aGateway ? -1 : 1;
        }
        if (aAi !== bAi) {
            return aAi ? -1 : 1;
        }

        let valueA = a[sortField];
        let valueB = b[sortField];

        if (valueA === null || valueA === undefined) valueA = '';
        if (valueB === null || valueB === undefined) valueB = '';

        if (sortField === 'createTime' || sortField === 'updateTime') {
            valueA = valueA ? new Date(valueA).getTime() : 0;
            valueB = valueB ? new Date(valueB).getTime() : 0;
        } else if (typeof valueA === 'string') {
            valueA = valueA.toLowerCase();
            valueB = valueB.toLowerCase();
        }

        let result = 0;
        if (valueA < valueB) result = -1;
        else if (valueA > valueB) result = 1;

        return sortOrder === 'asc' ? result : -result;
    });
}

function renderOverview(list) {
    const latestConfig = [...configList].sort((a, b) => new Date(b.updateTime || 0) - new Date(a.updateTime || 0))[0];
    const aiConfigs = configList.filter(item => AI_CONFIG_KEYS.includes(item.configKey));
    const gatewayConfigs = configList.filter(item => item.configKey && item.configKey.startsWith(GATEWAY_CONFIG_PREFIX));

    setText('configTotalCount', configList.length);
    setText('configAiCount', aiConfigs.length);
    setText('configGatewayCount', gatewayConfigs.length);
    setText('configFilteredCount', list.length);
    setText('configLastUpdated', latestConfig ? formatTime(latestConfig.updateTime) : '--');

    renderAiCard('ai.model.url', false);
    renderAiCard('ai.model.apiKey', true);
}

function renderAiCard(configKey, masked) {
    const config = configList.find(item => item.configKey === configKey);
    const targetId = configKey === 'ai.model.url' ? 'aiModelUrlValue' : 'aiModelApiKeyValue';
    const el = document.getElementById(targetId);
    if (!el) return;

    const value = config?.configValue || '';
    if (!value) {
        el.textContent = '未配置';
        el.style.color = 'var(--text-disabled)';
        return;
    }

    el.textContent = masked ? maskConfigValue(value) : value;
    el.style.color = 'var(--text-primary)';
}

function updateGatewayDefenseDisplay() {
    const blacklistConfig = configList.find(item => item.configKey === 'gateway.defense.blacklist.enabled');
    const rateLimitConfig = configList.find(item => item.configKey === 'gateway.defense.rate-limit.enabled');
    const maliciousConfig = configList.find(item => item.configKey === 'gateway.defense.malicious-request.enabled');

    setHtml('gatewayBlacklistEnabled', formatEnabledStatusHtml(blacklistConfig?.configValue));
    setHtml('gatewayRateLimitEnabled', formatEnabledStatusHtml(rateLimitConfig?.configValue));
    setHtml('gatewayMaliciousEnabled', formatEnabledStatusHtml(maliciousConfig?.configValue));
}

function formatEnabledStatus(value) {
    if (value === 'true') return '已开启';
    if (value === 'false') return '已关闭';
    return '--';
}

function formatEnabledStatusHtml(value) {
    if (value === 'true') return '<span class="defense-switch-value enabled">已开启</span>';
    if (value === 'false') return '<span class="defense-switch-value disabled">已关闭</span>';
    return '<span class="defense-switch-value">--</span>';
}

function setHtml(id, value) {
    const el = document.getElementById(id);
    if (el) el.innerHTML = value;
}

function renderConfigTable(list) {
    const tbody = document.getElementById('configTableBody');
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
        return;
    }

    const cell = TableUtils.cell;
    cell._currentTableBodyId = 'configTableBody';
    
    tbody.innerHTML = list.map(config => {
        const isAiConfig = AI_CONFIG_KEYS.includes(config.configKey);
        const isGatewayConfig = config.configKey && config.configKey.startsWith(GATEWAY_CONFIG_PREFIX);
        const badgeHtml = isAiConfig ? '<span class="ai-badge">AI</span>' : 
                          isGatewayConfig ? '<span class="gateway-badge">网关</span>' : '';
        const actionButtons = getActionButtons(config);
        const buttonCount = actionButtons.filter(btn => btn.visible !== false).length;
        const actionWidth = buttonCount >= 3 ? '220px' : '160px';
        
        return `
            <tr>
                <td>${config.id}</td>
                <td>
                    <div class="config-table-key">
                        <code>${cell.escapeHtml(config.configKey)}</code>
                        ${badgeHtml}
                    </div>
                </td>
                ${cell.renderCell(formatConfigValue(config), { maxLength: 40, showTooltip: true })}
                ${cell.renderCell(config.description, { maxLength: 40 })}
                <td>${formatTime(config.createTime)}</td>
                <td>${formatTime(config.updateTime)}</td>
                ${cell.renderActionCell(actionButtons, { width: actionWidth })}
            </tr>
        `;
    }).join('');
    
    cell._currentTableBodyId = null;
    TableUtils.bindTooltip(tbody);
}

function getActionButtons(config) {
    const buttons = [
        { text: '编辑', type: 'primary', onClick: `showEditConfigModal(${config.id})` },
        { text: '删除', type: 'danger', onClick: `deleteConfig(${config.id})` }
    ];
    
    if (config.configKey && config.configKey.startsWith(GATEWAY_CONFIG_PREFIX)) {
        buttons.splice(1, 0, { text: '推送', type: 'warning', onClick: `pushSingleConfig(${config.id})` });
    }
    
    return buttons;
}

function searchConfig() {
    currentFilterMode = 'ALL';
    applyFilters();
}

function resetSearch() {
    currentFilterMode = 'ALL';
    document.getElementById('configKey').value = '';
    document.getElementById('configCategory').value = 'ALL';
    applyFilters();
}

function filterByCategory() {
    currentFilterMode = 'ALL';
    applyFilters();
}

function filterAiConfigs() {
    currentFilterMode = 'AI_ONLY';
    document.getElementById('configCategory').value = 'AI';
    applyFilters();
}

function filterGatewayConfigs() {
    currentFilterMode = 'GATEWAY_ONLY';
    document.getElementById('configCategory').value = 'GATEWAY';
    applyFilters();
}

function editAiConfig(configKey) {
    currentFilterMode = 'AI_ONLY';
    document.getElementById('configKey').value = configKey;
    const config = configList.find(item => item.configKey === configKey);
    if (config) {
        applyFilters();
        showEditConfigModal(config.id);
        return;
    }
    showAddConfigModal({
        configKey,
        description: configKey === 'ai.model.url' ? '全局AI大模型接口地址' : '全局AI大模型API密钥'
    });
}

function showAddConfigModal(preset = {}) {
    document.getElementById('modalTitle').textContent = '添加配置';
    document.getElementById('configForm').reset();
    document.getElementById('configId').value = '';
    document.getElementById('configKeyInput').value = preset.configKey || '';
    document.getElementById('configValueInput').value = preset.configValue || '';
    document.getElementById('descriptionInput').value = preset.description || '';
    document.getElementById('configKeyInput').readOnly = false;
    document.getElementById('configModal').style.display = 'flex';
}

function showEditConfigModal(id) {
    const config = configList.find(item => item.id === id);
    if (!config) {
        message.error('配置不存在');
        return;
    }

    document.getElementById('modalTitle').textContent = '编辑配置';
    document.getElementById('configId').value = config.id;
    document.getElementById('configKeyInput').value = config.configKey;
    document.getElementById('configValueInput').value = config.configValue || '';
    document.getElementById('descriptionInput').value = config.description || '';
    document.getElementById('configKeyInput').readOnly = false;
    document.getElementById('configModal').style.display = 'flex';
}

function closeConfigModal() {
    document.getElementById('configModal').style.display = 'none';
}

async function saveConfig() {
    const configId = document.getElementById('configId').value;
    const configKey = document.getElementById('configKeyInput').value.trim();
    const configValue = document.getElementById('configValueInput').value.trim();
    const description = document.getElementById('descriptionInput').value.trim();

    if (!configKey) {
        message.error('配置键不能为空');
        return;
    }
    if (!configValue) {
        message.error('配置值不能为空');
        return;
    }

    const data = { configKey, configValue, description };
    try {
        if (configId) {
            data.id = parseInt(configId, 10);
            await http.post('/config/update', data);
            message.success('更新配置成功');
        } else {
            await http.post('/config/add', data);
            message.success('添加配置成功');
        }
        closeConfigModal();
        await loadConfigList();
    } catch (error) {
        console.error('保存配置失败:', error);
        message.error('保存配置失败：' + (error.message || '未知错误'));
    }
}

async function deleteConfig(id) {
    if (!confirm('确定要删除该配置吗？')) return;

    try {
        await http.delete(`/config/${id}`);
        message.success('删除配置成功');
        await loadConfigList();
    } catch (error) {
        console.error('删除配置失败:', error);
        message.error('删除配置失败：' + (error.message || '未知错误'));
    }
}

async function refreshConfig() {
    try {
        await http.post('/config/refresh');
        message.success('配置缓存刷新成功');
        await loadConfigList();
    } catch (error) {
        console.error('刷新配置缓存失败:', error);
        message.error('刷新配置缓存失败：' + (error.message || '未知错误'));
    }
}

async function checkGatewayStatus() {
    const statusEl = document.getElementById('gatewaySyncStatus');
    const countEl = document.getElementById('gatewayConfigCount');
    const lastSyncEl = document.getElementById('gatewayLastSync');
    const resultEl = document.getElementById('gatewaySyncResult');
    
    statusEl.textContent = '检测中...';
    statusEl.className = 'gateway-badge';
    
    try {
        const response = await http.get('/gateway/config/health');
        if (response.success) {
            statusEl.textContent = '在线';
            statusEl.classList.add('online');
            countEl.textContent = response.configCount || '--';
            lastSyncEl.textContent = formatTime(response.timestamp);
            resultEl.textContent = '正常';
        } else {
            throw new Error('健康检查失败');
        }
    } catch (error) {
        console.error('检查网关状态失败:', error);
        statusEl.textContent = '离线';
        statusEl.classList.add('offline');
        countEl.textContent = '--';
        lastSyncEl.textContent = '--';
        resultEl.textContent = '无法连接';
    }
}

async function pushSingleConfig(id) {
    const config = configList.find(item => item.id === id);
    if (!config) {
        message.error('配置不存在');
        return;
    }
    
    if (!confirm(`确定要将配置 "${config.configKey}" 推送到网关吗？`)) return;
    
    try {
        const response = await http.post('/gateway/config/push', {
            configKey: config.configKey,
            configValue: config.configValue
        });
        
        if (response.success) {
            message.success('配置推送成功');
            checkGatewayStatus();
        } else {
            message.error('配置推送失败：' + (response.message || '未知错误'));
        }
    } catch (error) {
        console.error('推送配置失败:', error);
        message.error('推送配置失败：' + (error.message || '未知错误'));
    }
}

async function pushGatewayConfigs() {
    const gatewayConfigs = configList.filter(item => 
        item.configKey && item.configKey.startsWith(GATEWAY_CONFIG_PREFIX)
    );
    
    if (gatewayConfigs.length === 0) {
        message.warning('没有网关配置需要推送');
        return;
    }
    
    if (!confirm(`确定要将 ${gatewayConfigs.length} 项网关配置推送到网关吗？`)) return;
    
    try {
        const configs = {};
        gatewayConfigs.forEach(item => {
            configs[item.configKey] = item.configValue;
        });
        
        const response = await http.post('/gateway/config/sync', { configs });
        
        if (response.success) {
            message.success(`成功推送 ${gatewayConfigs.length} 项网关配置`);
            checkGatewayStatus();
        } else {
            message.error('推送网关配置失败：' + (response.message || '未知错误'));
        }
    } catch (error) {
        console.error('推送网关配置失败:', error);
        message.error('推送网关配置失败：' + (error.message || '未知错误'));
    }
}

async function pushAllToGateway() {
    if (!confirm('确定要将所有配置推送到网关吗？这将同步所有网关配置项。')) return;
    
    try {
        const response = await http.post('/gateway/config/refresh');
        
        if (response.success) {
            message.success('配置已成功推送到网关');
            checkGatewayStatus();
        } else {
            message.error('推送配置失败：' + (response.message || '未知错误'));
        }
    } catch (error) {
        console.error('推送全部配置失败:', error);
        message.error('推送全部配置失败：' + (error.message || '未知错误'));
    }
}

function showGatewayDefenseModal() {
    const blacklistConfig = configList.find(item => item.configKey === 'gateway.defense.blacklist.enabled');
    const rateLimitConfig = configList.find(item => item.configKey === 'gateway.defense.rate-limit.enabled');
    const maliciousConfig = configList.find(item => item.configKey === 'gateway.defense.malicious-request.enabled');
    const thresholdConfig = configList.find(item => item.configKey === 'gateway.defense.rate-limit.default-threshold');
    const expireConfig = configList.find(item => item.configKey === 'gateway.defense.blacklist.default-expire-seconds');

    document.getElementById('gatewayBlacklistSwitch').value = blacklistConfig?.configValue || 'true';
    document.getElementById('gatewayRateLimitSwitch').value = rateLimitConfig?.configValue || 'true';
    document.getElementById('gatewayMaliciousSwitch').value = maliciousConfig?.configValue || 'true';
    document.getElementById('gatewayRateLimitThreshold').value = thresholdConfig?.configValue || '10';
    document.getElementById('gatewayBlacklistExpire').value = expireConfig?.configValue || '600';

    document.getElementById('gatewayDefenseModal').style.display = 'flex';
}

function closeGatewayDefenseModal() {
    document.getElementById('gatewayDefenseModal').style.display = 'none';
}

async function saveGatewayDefenseConfig() {
    const configs = {
        'gateway.defense.blacklist.enabled': document.getElementById('gatewayBlacklistSwitch').value,
        'gateway.defense.rate-limit.enabled': document.getElementById('gatewayRateLimitSwitch').value,
        'gateway.defense.malicious-request.enabled': document.getElementById('gatewayMaliciousSwitch').value,
        'gateway.defense.rate-limit.default-threshold': document.getElementById('gatewayRateLimitThreshold').value,
        'gateway.defense.blacklist.default-expire-seconds': document.getElementById('gatewayBlacklistExpire').value
    };

    try {
        let successCount = 0;
        for (const [key, value] of Object.entries(configs)) {
            const existingConfig = configList.find(item => item.configKey === key);
            if (existingConfig) {
                await http.post('/config/update', { id: existingConfig.id, configKey: key, configValue: value });
            } else {
                await http.post('/config/add', { configKey: key, configValue: value });
            }
            successCount++;
        }

        const pushResponse = await http.post('/gateway/config/sync', { configs });
        
        if (pushResponse.success) {
            message.success(`已保存 ${successCount} 项配置并推送到网关`);
            closeGatewayDefenseModal();
            await loadConfigList();
            checkGatewayStatus();
        } else {
            message.warning('配置已保存，但推送到网关失败');
            closeGatewayDefenseModal();
            await loadConfigList();
        }
    } catch (error) {
        console.error('保存网关防御配置失败:', error);
        message.error('保存配置失败：' + (error.message || '未知错误'));
    }
}

function formatConfigValue(config) {
    const value = config?.configValue || '';
    return config?.configKey === 'ai.model.apiKey' ? maskConfigValue(value) : value || '-';
}

function maskConfigValue(value) {
    if (!value) return '未配置';
    if (value.length <= 8) return '*'.repeat(value.length);
    return `${value.slice(0, 4)}****${value.slice(-4)}`;
}

function formatTime(value) {
    return value ? dateFormat.format(value) : '--';
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}
