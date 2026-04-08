let whitelistTable;

document.addEventListener('DOMContentLoaded', function() {
    initWhitelistTable();
});

function initWhitelistTable() {
    whitelistTable = TableUtils.createInstance({
        instanceName: 'whitelistTable',
        apiUrl: '/whitelist/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'whitelistTableBody',
 
        paginationEl: 'pagination',
        colspan: 7,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
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
    whitelistTable.loadData();
}

function renderWhitelistType(type) {
    const typeMap = {
        'PATH': '<span class="badge badge-info">路径白名单</span>',
        'HEADER': '<span class="badge badge-warning">请求头白名单</span>',
        'IP': '<span class="badge badge-success">IP白名单</span>'
    };
    return typeMap[type] || type;
}

function searchWhitelists() {
    const whitelistType = whitelistTable.getSearchSelectValue('whitelistType');
    const whitelistValue = whitelistTable.getSearchValue('whitelistValue');
    const enabled = whitelistTable.getSearchSelectValue('enabled');
    
    const params = {};
    if (whitelistType) params.whitelistType = whitelistType;
    if (whitelistValue) params.whitelistValue = whitelistValue;
    if (enabled !== '') params.enabled = enabled;
    
    whitelistTable.search(params);
}

function resetSearch() {
    document.getElementById('whitelistType').value = '';
    document.getElementById('whitelistValue').value = '';
    document.getElementById('enabled').value = '';
    whitelistTable.resetSearch();
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

function editWhitelist(id) {
    http.get('/whitelist/' + id)
        .then(result => {
        if (result) {
            document.getElementById('whitelistId').value = result.id;
            document.getElementById('whitelistTypeInput').value = result.whitelistType || '';
            document.getElementById('whitelistValueInput').value = result.whitelistValue || '';
            document.getElementById('descriptionInput').value = result.description || '';
            document.getElementById('priorityInput').value = result.priority || 100;
            document.getElementById('enabledInput').value = result.enabled !== undefined ? result.enabled : 1;
            
            document.getElementById('whitelistModalTitle').textContent = '编辑白名单';
            document.getElementById('whitelistModal').style.display = 'flex';
        }
    })
    .catch(error => {
        message.error('获取白名单详情失败: ' + (error.message || '未知错误'));
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

function showAddWhitelistModal() {
    document.getElementById('whitelistId').value = '';
    document.getElementById('whitelistTypeInput').value = '';
    document.getElementById('whitelistValueInput').value = '';
    document.getElementById('descriptionInput').value = '';
    document.getElementById('priorityInput').value = '100';
    document.getElementById('enabledInput').value = '1';
    
    document.getElementById('whitelistModalTitle').textContent = '新增白名单';
    document.getElementById('whitelistModal').style.display = 'flex';
}

function closeWhitelistModal() {
    document.getElementById('whitelistModal').style.display = 'none';
}

function saveWhitelist() {
    const id = document.getElementById('whitelistId').value;
    const whitelistType = document.getElementById('whitelistTypeInput').value.trim();
    const whitelistValue = document.getElementById('whitelistValueInput').value.trim();
    const description = document.getElementById('descriptionInput').value.trim();
    const priority = parseInt(document.getElementById('priorityInput').value) || 100;
    const enabled = parseInt(document.getElementById('enabledInput').value);
    
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

function refreshWhitelistCache() {
    http.post('/whitelist/refresh')
        .then(result => {
        message.success('刷新缓存成功');
    })
    .catch(error => {
        message.error('刷新缓存失败: ' + (error.message || '未知错误'));
    });
}

document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeWhitelistModal();
    }
});
