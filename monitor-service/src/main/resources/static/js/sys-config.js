/**
 * 系统配置管理页面
 */

let configList = [];

document.addEventListener('DOMContentLoaded', function() {
    loadConfigList();
});

async function loadConfigList() {
    try {
        const configKey = document.getElementById('configKey').value.trim();
        const url = configKey ? `/config/list?key=${encodeURIComponent(configKey)}` : '/config/list';
        
        configList = await http.get(url);
        renderConfigTable(configList);
    } catch (error) {
        console.error('加载配置列表失败:', error);
        renderConfigTable([]);
    }
}

function renderConfigTable(list) {
    const tbody = document.getElementById('configTableBody');
    
    if (!list || list.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center">暂无数据</td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = list.map(config => `
        <tr>
            <td>${config.id}</td>
            <td><code>${config.configKey}</code></td>
            <td>${config.configValue}</td>
            <td>${config.description || '-'}</td>
            <td>${dateFormat.format(config.createTime)}</td>
            <td>${dateFormat.format(config.updateTime)}</td>
            <td>
                <button class="btn btn-sm btn-primary" onclick="showEditConfigModal(${config.id})">编辑</button>
                <button class="btn btn-sm btn-danger" onclick="deleteConfig(${config.id})">删除</button>
            </td>
        </tr>
    `).join('');
}

function searchConfig() {
    loadConfigList();
}

function resetSearch() {
    document.getElementById('configKey').value = '';
    loadConfigList();
}

function showAddConfigModal() {
    document.getElementById('modalTitle').textContent = '添加配置';
    document.getElementById('configForm').reset();
    document.getElementById('configId').value = '';
    document.getElementById('configModal').style.display = 'flex';
}

function showEditConfigModal(id) {
    const config = configList.find(c => c.id === id);
    if (!config) {
        message.error('配置不存在');
        return;
    }
    
    document.getElementById('modalTitle').textContent = '编辑配置';
    document.getElementById('configId').value = config.id;
    document.getElementById('configKeyInput').value = config.configKey;
    document.getElementById('configValueInput').value = config.configValue;
    document.getElementById('descriptionInput').value = config.description || '';
    
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
    
    try {
        const data = {
            configKey,
            configValue,
            description
        };
        
        if (configId) {
            data.id = parseInt(configId);
            await http.post('/config/update', data);
            message.success('更新配置成功');
        } else {
            await http.post('/config/add', data);
            message.success('添加配置成功');
        }
        
        closeConfigModal();
        loadConfigList();
    } catch (error) {
        console.error('保存配置失败:', error);
    }
}

async function deleteConfig(id) {
    if (!confirm('确定要删除该配置吗？')) {
        return;
    }
    
    try {
        await http.delete(`/config/${id}`);
        message.success('删除配置成功');
        loadConfigList();
    } catch (error) {
        console.error('删除配置失败:', error);
    }
}

async function refreshConfig() {
    try {
        await http.post('/config/refresh');
        message.success('配置缓存刷新成功');
        loadConfigList();
    } catch (error) {
        console.error('刷新配置缓存失败:', error);
    }
}