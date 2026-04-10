const currentPage = {
    pageNum: 1,
    pageSize: 10
};

document.addEventListener('DOMContentLoaded', function() {
    loadTargets();
});

function loadTargets() {
    const params = {
        targetName: document.getElementById('targetName').value,
        targetType: document.getElementById('targetType').value,
        enabled: document.getElementById('enabled').value,
        page: currentPage.pageNum,
        size: currentPage.pageSize
    };

    http.get('/api/scan-target/list', { params: params })
        .then(response => {
            if (response.code === 200) {
                renderTable(response.data.list);
                renderPagination(response.data.total);
            } else {
                Message.error(response.message || '加载失败');
            }
        })
        .catch(error => {
            console.error('加载扫描目标失败：', error);
            Message.error('加载失败');
        });
}

function renderTable(list) {
    const tbody = document.getElementById('tableBody');
    
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">暂无数据</td></tr>';
        return;
    }

    tbody.innerHTML = list.map(item => `
        <tr>
            <td>${item.id}</td>
            <td>${item.targetName || '-'}</td>
            <td>${item.targetUrl || '-'}</td>
            <td>${formatTargetType(item.targetType)}</td>
            <td>${item.description || '-'}</td>
            <td>
                <span class="tag ${item.enabled === 1 ? 'tag-success' : 'tag-default'}">
                    ${item.enabled === 1 ? '启用' : '禁用'}
                </span>
            </td>
            <td>${formatTime(item.createTime)}</td>
            <td>
                <button class="btn btn-sm btn-primary" onclick="editTarget(${item.id})">编辑</button>
                <button class="btn btn-sm ${item.enabled === 1 ? 'btn-warning' : 'btn-success'}" 
                        onclick="toggleEnabled(${item.id}, ${item.enabled === 1 ? 0 : 1})">
                    ${item.enabled === 1 ? '禁用' : '启用'}
                </button>
                <button class="btn btn-sm btn-danger" onclick="deleteTarget(${item.id})">删除</button>
            </td>
        </tr>
    `).join('');
}

function renderPagination(total) {
    const totalPages = Math.ceil(total / currentPage.pageSize);
    const pagination = document.getElementById('pagination');
    
    if (totalPages <= 1) {
        pagination.innerHTML = '';
        return;
    }

    let html = '';
    
    if (currentPage.pageNum > 1) {
        html += `<button class="btn btn-sm btn-default" onclick="changePage(${currentPage.pageNum - 1})">上一页</button>`;
    }
    
    for (let i = 1; i <= totalPages; i++) {
        if (i === currentPage.pageNum) {
            html += `<button class="btn btn-sm btn-primary">${i}</button>`;
        } else {
            html += `<button class="btn btn-sm btn-default" onclick="changePage(${i})">${i}</button>`;
        }
    }
    
    if (currentPage.pageNum < totalPages) {
        html += `<button class="btn btn-sm btn-default" onclick="changePage(${currentPage.pageNum + 1})">下一页</button>`;
    }
    
    pagination.innerHTML = html;
}

function changePage(pageNum) {
    currentPage.pageNum = pageNum;
    loadTargets();
}

function searchTargets() {
    currentPage.pageNum = 1;
    loadTargets();
}

function resetSearch() {
    document.getElementById('targetName').value = '';
    document.getElementById('targetType').value = '';
    document.getElementById('enabled').value = '';
    currentPage.pageNum = 1;
    loadTargets();
}

function showAddModal() {
    document.getElementById('modalTitle').textContent = '新增扫描目标';
    document.getElementById('targetForm').reset();
    document.getElementById('targetId').value = '';
    document.getElementById('modal').style.display = 'flex';
}

function editTarget(id) {
    http.get(`/api/scan-target/${id}`)
        .then(response => {
            if (response.code === 200) {
                const data = response.data;
                document.getElementById('modalTitle').textContent = '编辑扫描目标';
                document.getElementById('targetId').value = data.id;
                document.getElementById('formTargetName').value = data.targetName || '';
                document.getElementById('formTargetUrl').value = data.targetUrl || '';
                document.getElementById('formTargetType').value = data.targetType || 'TEST';
                document.getElementById('formDescription').value = data.description || '';
                document.getElementById('formEnabled').value = data.enabled !== undefined ? data.enabled : 1;
                document.getElementById('modal').style.display = 'flex';
            } else {
                Message.error(response.message || '获取详情失败');
            }
        })
        .catch(error => {
            console.error('获取扫描目标详情失败：', error);
            Message.error('获取详情失败');
        });
}

function saveTarget() {
    const id = document.getElementById('targetId').value;
    const data = {
        targetName: document.getElementById('formTargetName').value,
        targetUrl: document.getElementById('formTargetUrl').value,
        targetType: document.getElementById('formTargetType').value,
        description: document.getElementById('formDescription').value,
        enabled: parseInt(document.getElementById('formEnabled').value)
    };

    if (!data.targetName || !data.targetUrl || !data.targetType) {
        Message.error('请填写必填项');
        return;
    }

    const request = id 
        ? http.put(`/api/scan-target/update`, Object.assign({ id: parseInt(id) }, data))
        : http.post('/api/scan-target/add', data);

    request
        .then(response => {
            if (response.code === 200) {
                Message.success(id ? '更新成功' : '创建成功');
                closeModal();
                loadTargets();
            } else {
                Message.error(response.message || '保存失败');
            }
        })
        .catch(error => {
            console.error('保存扫描目标失败：', error);
            Message.error('保存失败');
        });
}

function toggleEnabled(id, enabled) {
    http.put(`/api/scan-target/${id}/enabled?enabled=${enabled}`)
        .then(response => {
            if (response.code === 200) {
                Message.success(enabled === 1 ? '已启用' : '已禁用');
                loadTargets();
            } else {
                Message.error(response.message || '操作失败');
            }
        })
        .catch(error => {
            console.error('更新启用状态失败：', error);
            Message.error('操作失败');
        });
}

function deleteTarget(id) {
    if (!confirm('确定要删除该扫描目标吗？')) {
        return;
    }

    http.delete(`/api/scan-target/${id}`)
        .then(response => {
            if (response.code === 200) {
                Message.success('删除成功');
                loadTargets();
            } else {
                Message.error(response.message || '删除失败');
            }
        })
        .catch(error => {
            console.error('删除扫描目标失败：', error);
            Message.error('删除失败');
        });
}

function closeModal() {
    document.getElementById('modal').style.display = 'none';
}

function formatTargetType(type) {
    const types = {
        'PRODUCTION': '生产环境',
        'TEST': '测试环境',
        'DEVELOPMENT': '开发环境'
    };
    return types[type] || type || '-';
}

function formatTime(time) {
    if (!time) return '-';
    return time.replace('T', ' ').substring(0, 19);
}

window.onclick = function(event) {
    const modal = document.getElementById('modal');
    if (event.target === modal) {
        closeModal();
    }
};
