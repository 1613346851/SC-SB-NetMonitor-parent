let configList = [];
let filteredConfigList = [];
let currentFilterMode = 'ALL';
let sortField = 'id';
let sortOrder = 'desc';

const AI_CONFIG_KEYS = ['ai.model.url', 'ai.model.apiKey'];

document.addEventListener('DOMContentLoaded', function() {
    bindConfigModal();
    initTableSorting();
    loadConfigList();
});

function bindConfigModal() {
    const modal = document.getElementById('configModal');
    modal?.addEventListener('click', function(event) {
        if (event.target === this) {
            closeConfigModal();
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
    
    updateSortIcons(table);
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
    let list = [...configList];

    if (currentFilterMode === 'AI_ONLY') {
        list = list.filter(item => AI_CONFIG_KEYS.includes(item.configKey));
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
        const aAi = AI_CONFIG_KEYS.includes(a.configKey);
        const bAi = AI_CONFIG_KEYS.includes(b.configKey);
        
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

    setText('configTotalCount', configList.length);
    setText('configAiCount', aiConfigs.length);
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
        el.classList.add('empty');
        return;
    }

    el.textContent = masked ? maskConfigValue(value) : value;
    el.classList.remove('empty');
}

function renderConfigTable(list) {
    const tbody = document.getElementById('configTableBody');
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
        return;
    }

    const cell = TableUtils.cell;
    
    tbody.innerHTML = list.map(config => {
        const isAiConfig = AI_CONFIG_KEYS.includes(config.configKey);
        return `
            <tr>
                <td>${config.id}</td>
                <td>
                    <div class="config-table-key">
                        <code>${cell.escapeHtml(config.configKey)}</code>
                        ${isAiConfig ? '<span class="ai-badge">AI</span>' : ''}
                    </div>
                </td>
                ${cell.renderCell(formatConfigValue(config), { maxLength: 40, showTooltip: true })}
                ${cell.renderCell(config.description, { maxLength: 40 })}
                <td>${formatTime(config.createTime)}</td>
                <td>${formatTime(config.updateTime)}</td>
                ${cell.renderActionCell(`
                    ${cell.renderButton('编辑', 'primary', `showEditConfigModal(${config.id})`)}
                    ${cell.renderButton('删除', 'danger', `deleteConfig(${config.id})`)}
                `)}
            </tr>
        `;
    }).join('');
    
    TableUtils.bindTooltip(tbody);
}

function searchConfig() {
    currentFilterMode = 'ALL';
    applyFilters();
}

function resetSearch() {
    currentFilterMode = 'ALL';
    document.getElementById('configKey').value = '';
    applyFilters();
}

function filterAiConfigs() {
    currentFilterMode = 'AI_ONLY';
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
