let currentPage = 1;
let pageSize = 10;
let currentStatus = '';
let selectedIds = [];
let currentAlertId = null;

function init() {
    loadStats();
    loadAlerts();
}

function loadStats() {
    http.get('/alert/stats')
        .then(function(response) {
            if (response.code === 200) {
                const stats = response.data;
                document.getElementById('statCritical').textContent = stats.critical || 0;
                document.getElementById('statHigh').textContent = stats.high || 0;
                document.getElementById('statMedium').textContent = stats.medium || 0;
                document.getElementById('statLow').textContent = stats.low || 0;
                document.getElementById('statPending').textContent = stats.pending || 0;
            }
        })
        .catch(function(error) {
            console.error('加载统计数据失败:', error);
        });
}

function loadAlerts() {
    const params = buildQueryParams();
    
    http.get('/alert/list', params)
        .then(function(response) {
            if (response.code === 200) {
                renderTable(response.data);
                renderPagination(response.data);
            } else {
                message.error(response.message || '加载失败');
            }
        })
        .catch(function(error) {
            console.error('加载告警列表失败:', error);
            message.error('加载失败');
        });
}

function buildQueryParams() {
    const params = {
        pageNum: currentPage,
        pageSize: pageSize
    };

    const alertLevel = document.getElementById('alertLevel').value;
    const sourceIp = document.getElementById('sourceIp').value;
    const attackType = document.getElementById('attackType').value;
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;

    if (alertLevel) params.alertLevel = alertLevel;
    if (sourceIp) params.sourceIp = sourceIp;
    if (attackType) params.attackType = attackType;
    if (currentStatus !== '') params.status = currentStatus;
    if (startDate) params.startTime = startDate + ' 00:00:00';
    if (endDate) params.endTime = endDate + ' 23:59:59';

    return params;
}

function renderTable(data) {
    const tbody = document.getElementById('alertTableBody');
    const list = data.list || [];

    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" class="text-center">暂无数据</td></tr>';
        return;
    }

    let html = '';
    list.forEach(function(alert) {
        const levelClass = getLevelClass(alert.alertLevel);
        const levelBadge = getLevelBadge(alert.alertLevel);
        const statusText = getStatusText(alert.status);
        const statusClass = getStatusClass(alert.status);
        
        html += '<tr class="' + levelClass + '">';
        html += '<td class="checkbox-cell"><input type="checkbox" class="alert-checkbox" value="' + alert.id + '" onchange="updateSelectedIds()"></td>';
        html += '<td>' + alert.id + '</td>';
        html += '<td><span class="badge ' + levelBadge + '">' + alert.alertLevelChinese + '</span></td>';
        html += '<td>' + escapeHtml(alert.sourceIp) + '</td>';
        html += '<td>' + escapeHtml(alert.alertTitle) + '</td>';
        html += '<td>' + alert.attackTypeChinese + '</td>';
        html += '<td>' + (alert.aggregateCount || 1) + '</td>';
        html += '<td>' + formatDateTime(alert.firstOccurTime) + '</td>';
        html += '<td><span class="badge ' + statusClass + '">' + statusText + '</span></td>';
        html += '<td class="action-cell">';
        html += '<button class="btn btn-sm btn-info" onclick="viewAlert(' + alert.id + ')">详情</button>';
        if (alert.status === 0) {
            html += '<button class="btn btn-sm btn-primary" onclick="quickConfirm(' + alert.id + ')">确认</button>';
        }
        html += '</td>';
        html += '</tr>';
    });

    tbody.innerHTML = html;
    selectedIds = [];
    document.getElementById('selectAll').checked = false;
}

function renderPagination(data) {
    const pagination = document.getElementById('pagination');
    const total = data.total || 0;
    const totalPages = Math.ceil(total / pageSize);

    if (totalPages <= 1) {
        pagination.innerHTML = '';
        return;
    }

    let html = '';
    html += '<button class="page-btn" onclick="goToPage(' + (currentPage - 1) + ')" ' + (currentPage === 1 ? 'disabled' : '') + '>上一页</button>';
    
    const startPage = Math.max(1, currentPage - 2);
    const endPage = Math.min(totalPages, currentPage + 2);

    if (startPage > 1) {
        html += '<button class="page-btn" onclick="goToPage(1)">1</button>';
        if (startPage > 2) {
            html += '<span class="page-ellipsis">...</span>';
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        html += '<button class="page-btn ' + (i === currentPage ? 'active' : '') + '" onclick="goToPage(' + i + ')">' + i + '</button>';
    }

    if (endPage < totalPages) {
        if (endPage < totalPages - 1) {
            html += '<span class="page-ellipsis">...</span>';
        }
        html += '<button class="page-btn" onclick="goToPage(' + totalPages + ')">' + totalPages + '</button>';
    }

    html += '<button class="page-btn" onclick="goToPage(' + (currentPage + 1) + ')" ' + (currentPage === totalPages ? 'disabled' : '') + '>下一页</button>';
    
    html += '<span class="page-info">共 ' + total + ' 条</span>';

    pagination.innerHTML = html;
}

function goToPage(page) {
    const totalPages = Math.ceil((document.querySelector('.page-info')?.textContent.match(/\d+/)?.[0] || 0) / pageSize);
    if (page < 1 || page > totalPages) return;
    currentPage = page;
    loadAlerts();
}

function switchTab(element, status) {
    document.querySelectorAll('.tab-item').forEach(function(tab) {
        tab.classList.remove('active');
    });
    element.classList.add('active');
    currentStatus = status;
    currentPage = 1;
    loadAlerts();
}

function searchAlerts() {
    currentPage = 1;
    loadAlerts();
}

function resetSearch() {
    document.getElementById('alertLevel').value = '';
    document.getElementById('sourceIp').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('startDate').value = '';
    document.getElementById('endDate').value = '';
    currentPage = 1;
    loadAlerts();
}

function viewAlert(id) {
    currentAlertId = id;
    http.get('/alert/' + id)
        .then(function(response) {
            if (response.code === 200) {
                showAlertDetail(response.data);
            } else {
                message.error(response.message || '获取详情失败');
            }
        })
        .catch(function(error) {
            console.error('获取告警详情失败:', error);
            message.error('获取详情失败');
        });
}

function showAlertDetail(alert) {
    const content = document.getElementById('alertDetailContent');
    
    let html = '<div class="alert-detail-row"><div class="alert-detail-label">告警ID</div><div class="alert-detail-value">' + escapeHtml(alert.alertId) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">告警级别</div><div class="alert-detail-value"><span class="badge ' + getLevelBadge(alert.alertLevel) + '">' + alert.alertLevelChinese + '</span></div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">告警标题</div><div class="alert-detail-value">' + escapeHtml(alert.alertTitle) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">源IP</div><div class="alert-detail-value">' + escapeHtml(alert.sourceIp) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">攻击类型</div><div class="alert-detail-value">' + alert.attackTypeChinese + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">聚合次数</div><div class="alert-detail-value">' + (alert.aggregateCount || 1) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">首次发生</div><div class="alert-detail-value">' + formatDateTime(alert.firstOccurTime) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">最近发生</div><div class="alert-detail-value">' + formatDateTime(alert.lastOccurTime) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">状态</div><div class="alert-detail-value">' + alert.statusChinese + '</div></div>';
    
    if (alert.alertContent) {
        html += '<div class="alert-detail-row"><div class="alert-detail-label">详情</div><div class="alert-detail-value">' + escapeHtml(alert.alertContent).replace(/\n/g, '<br>') + '</div></div>';
    }
    
    if (alert.status === 1) {
        html += '<div class="alert-detail-row"><div class="alert-detail-label">确认人</div><div class="alert-detail-value">' + escapeHtml(alert.confirmBy || '-') + '</div></div>';
        html += '<div class="alert-detail-row"><div class="alert-detail-label">确认时间</div><div class="alert-detail-value">' + formatDateTime(alert.confirmTime) + '</div></div>';
    }
    
    if (alert.status === 2) {
        html += '<div class="alert-detail-row"><div class="alert-detail-label">忽略人</div><div class="alert-detail-value">' + escapeHtml(alert.ignoreBy || '-') + '</div></div>';
        html += '<div class="alert-detail-row"><div class="alert-detail-label">忽略原因</div><div class="alert-detail-value">' + escapeHtml(alert.ignoreReason || '-') + '</div></div>';
        html += '<div class="alert-detail-row"><div class="alert-detail-label">忽略时间</div><div class="alert-detail-value">' + formatDateTime(alert.ignoreTime) + '</div></div>';
    }

    content.innerHTML = html;
    
    const confirmBtn = document.getElementById('confirmBtn');
    const ignoreBtn = document.getElementById('ignoreBtn');
    
    if (alert.status === 0) {
        confirmBtn.style.display = 'inline-block';
        ignoreBtn.style.display = 'inline-block';
    } else {
        confirmBtn.style.display = 'none';
        ignoreBtn.style.display = 'none';
    }

    document.getElementById('alertDetailModal').style.display = 'flex';
}

function closeAlertDetail() {
    document.getElementById('alertDetailModal').style.display = 'none';
    currentAlertId = null;
}

function confirmAlert() {
    if (!currentAlertId) return;
    
    http.post('/alert/' + currentAlertId + '/confirm')
        .then(function(response) {
            if (response.code === 200) {
                message.success('确认成功');
                closeAlertDetail();
                loadStats();
                loadAlerts();
            } else {
                message.error(response.message || '确认失败');
            }
        })
        .catch(function(error) {
            console.error('确认告警失败:', error);
            message.error('确认失败');
        });
}

function quickConfirm(id) {
    http.post('/alert/' + id + '/confirm')
        .then(function(response) {
            if (response.code === 200) {
                message.success('确认成功');
                loadStats();
                loadAlerts();
            } else {
                message.error(response.message || '确认失败');
            }
        })
        .catch(function(error) {
            console.error('确认告警失败:', error);
            message.error('确认失败');
        });
}

function ignoreAlert() {
    if (!currentAlertId) return;
    document.getElementById('ignoreReason').value = '';
    document.getElementById('ignoreReasonModal').style.display = 'flex';
}

function closeIgnoreModal() {
    document.getElementById('ignoreReasonModal').style.display = 'none';
}

function submitIgnore() {
    const reason = document.getElementById('ignoreReason').value;
    
    http.post('/alert/' + currentAlertId + '/ignore', { reason: reason })
        .then(function(response) {
            if (response.code === 200) {
                message.success('已忽略');
                closeIgnoreModal();
                closeAlertDetail();
                loadStats();
                loadAlerts();
            } else {
                message.error(response.message || '操作失败');
            }
        })
        .catch(function(error) {
            console.error('忽略告警失败:', error);
            message.error('操作失败');
        });
}

function toggleSelectAll() {
    const selectAll = document.getElementById('selectAll').checked;
    const checkboxes = document.querySelectorAll('.alert-checkbox');
    
    checkboxes.forEach(function(cb) {
        cb.checked = selectAll;
    });
    
    updateSelectedIds();
}

function updateSelectedIds() {
    const checkboxes = document.querySelectorAll('.alert-checkbox:checked');
    selectedIds = Array.from(checkboxes).map(function(cb) {
        return parseInt(cb.value);
    });
    
    const selectAll = document.getElementById('selectAll');
    const allCheckboxes = document.querySelectorAll('.alert-checkbox');
    selectAll.checked = selectedIds.length === allCheckboxes.length;
}

function batchConfirm() {
    if (selectedIds.length === 0) {
        message.warning('请选择要确认的告警');
        return;
    }
    
    if (!confirm('确定要批量确认选中的 ' + selectedIds.length + ' 条告警吗？')) {
        return;
    }
    
    http.post('/alert/batch-confirm', selectedIds)
        .then(function(response) {
            if (response.code === 200) {
                message.success('批量确认成功');
                loadStats();
                loadAlerts();
            } else {
                message.error(response.message || '批量确认失败');
            }
        })
        .catch(function(error) {
            console.error('批量确认失败:', error);
            message.error('批量确认失败');
        });
}

function batchDelete() {
    if (selectedIds.length === 0) {
        message.warning('请选择要删除的告警');
        return;
    }
    
    if (!confirm('确定要批量删除选中的 ' + selectedIds.length + ' 条告警吗？此操作不可恢复。')) {
        return;
    }
    
    http.delete('/alert/batch', selectedIds)
        .then(function(response) {
            if (response.code === 200) {
                message.success('批量删除成功');
                loadStats();
                loadAlerts();
            } else {
                message.error(response.message || '批量删除失败');
            }
        })
        .catch(function(error) {
            console.error('批量删除失败:', error);
            message.error('批量删除失败');
        });
}

function getLevelClass(level) {
    switch (level) {
        case 'CRITICAL': return 'alert-critical';
        case 'HIGH': return 'alert-high';
        case 'MEDIUM': return 'alert-medium';
        case 'LOW': return 'alert-low';
        default: return '';
    }
}

function getLevelBadge(level) {
    switch (level) {
        case 'CRITICAL': return 'level-badge-critical';
        case 'HIGH': return 'level-badge-high';
        case 'MEDIUM': return 'level-badge-medium';
        case 'LOW': return 'level-badge-low';
        default: return '';
    }
}

function getStatusText(status) {
    switch (status) {
        case 0: return '待处理';
        case 1: return '已确认';
        case 2: return '已忽略';
        default: return '未知';
    }
}

function getStatusClass(status) {
    switch (status) {
        case 0: return 'badge-warning';
        case 1: return 'badge-success';
        case 2: return 'badge-secondary';
        default: return '';
    }
}

function formatDateTime(dateStr) {
    if (!dateStr) return '-';
    return dateStr.replace('T', ' ').substring(0, 19);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

document.addEventListener('DOMContentLoaded', init);
