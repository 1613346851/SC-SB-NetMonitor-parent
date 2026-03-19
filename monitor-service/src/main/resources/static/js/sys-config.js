let configList = [];
let filteredConfigList = [];
let currentFilterMode = 'ALL';

const AI_CONFIG_KEYS = ['ai.model.url', 'ai.model.apiKey'];

document.addEventListener('DOMContentLoaded', function() {
    bindConfigModal();
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

    filteredConfigList = list.sort((a, b) => {
        const aAi = AI_CONFIG_KEYS.includes(a.configKey);
        const bAi = AI_CONFIG_KEYS.includes(b.configKey);
        if (aAi !== bAi) {
            return aAi ? -1 : 1;
        }
        return new Date(b.updateTime || 0).getTime() - new Date(a.updateTime || 0).getTime();
    });

    renderOverview(filteredConfigList);
    renderConfigTable(filteredConfigList);
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

    tbody.innerHTML = list.map(config => {
        const isAiConfig = AI_CONFIG_KEYS.includes(config.configKey);
        return `
            <tr>
                <td>${config.id}</td>
                <td>
                    <div class="config-table-key">
                        <code>${escapeHtml(config.configKey)}</code>
                        ${isAiConfig ? '<span class="ai-badge">AI</span>' : ''}
                    </div>
                </td>
                <td><span class="config-value-text" title="${escapeHtml(config.configValue || '')}">${escapeHtml(formatConfigValue(config))}</span></td>
                <td title="${escapeHtml(config.description || '')}">${escapeHtml(config.description || '-')}</td>
                <td>${formatTime(config.createTime)}</td>
                <td>${formatTime(config.updateTime)}</td>
                <td>
                    <button class="btn btn-sm btn-primary" onclick="showEditConfigModal(${config.id})">编辑</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteConfig(${config.id})">删除</button>
                </td>
            </tr>
        `;
    }).join('');
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
    if (!confirm('确定要删除该配置吗？')) {
        return;
    }

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
    if (!value) {
        return '未配置';
    }
    if (value.length <= 8) {
        return '*'.repeat(value.length);
    }
    return `${value.slice(0, 4)}****${value.slice(-4)}`;
}

function formatTime(value) {
    return value ? dateFormat.format(value) : '--';
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) {
        el.textContent = value;
    }
}

function escapeHtml(text) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
