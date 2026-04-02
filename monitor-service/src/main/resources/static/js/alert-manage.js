let alertTable;
let selectedIds = [];
let currentAlertId = null;

document.addEventListener('DOMContentLoaded', function() {
    loadStats();
    initAlertTable();
    
    const urlParams = new URLSearchParams(window.location.search);
    const alertId = urlParams.get('alertId');
    if (alertId) {
        setTimeout(function() {
            viewAlert(alertId);
        }, 500);
    }
});

function initAlertTable() {
    alertTable = TableUtils.createInstance({
        instanceName: 'alertTable',
        apiUrl: '/alert/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'alertTableBody',
        paginationEl: 'pagination',
        colspan: 11,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            const statusText = getStatusText(item.status);
            const statusClass = getStatusClass(item.status);
            const eventIdDisplay = item.eventId ? (item.eventId.length > 16 ? item.eventId.substring(0, 16) + '...' : item.eventId) : '-';
            const attackIdDisplay = item.attackId ? ('<a href="/attack?attackId=' + item.attackId + '" style="color: #4f46e5;">查看记录</a>') : '-';
            
            const buttons = [
                { text: '详情', type: 'info', onClick: `viewAlert(${item.id})` }
            ];
            
            if (item.status === 0) {
                buttons.push({ text: '确认', type: 'primary', onClick: `quickConfirm(${item.id})` });
            }
            
            return `
                <tr>
                    <td class="checkbox-cell"><input type="checkbox" class="alert-checkbox" value="${item.id}" onchange="updateSelectedIds()"></td>
                    <td>${item.id || '-'}</td>
                    <td>${cell.renderRiskLevel(item.alertLevel)}</td>
                    ${cell.renderCell(item.sourceIp, { maxLength: 20 })}
                    ${cell.renderCell(item.alertTitle, { maxLength: 40 })}
                    <td>${cell.renderAttackType(item.attackType)}</td>
                    <td>${attackIdDisplay}</td>
                    ${cell.renderCell(item.eventId, { maxLength: 16 })}
                    <td>${formatDateTime(item.createTime)}</td>
                    <td>${cell.renderStatus(item.status, 'handle')}</td>
                    ${cell.renderActionCell(buttons)}
                </tr>
            `;
        }
    });
    
    window.alertTable = alertTable;
    alertTable.loadData();
}

function loadStats() {
    http.get('/alert/stats')
        .then(function(stats) {
            document.getElementById('statCritical').textContent = stats.critical || 0;
            document.getElementById('statHigh').textContent = stats.high || 0;
            document.getElementById('statMedium').textContent = stats.medium || 0;
            document.getElementById('statLow').textContent = stats.low || 0;
            document.getElementById('statPending').textContent = stats.pending || 0;
            
            updateTabBadges(stats);
        })
        .catch(function(error) {
            console.error('加载统计数据失败:', error);
        });
}

function updateTabBadges(stats) {
    var statusStats = stats.statusStats || [];
    var pending = 0, confirmed = 0, ignored = 0;
    
    statusStats.forEach(function(item) {
        var status = parseInt(item.status);
        var count = parseInt(item.count) || 0;
        
        if (status === 0) pending = count;
        else if (status === 1) confirmed = count;
        else if (status === 2) ignored = count;
    });
    
    var total = pending + confirmed + ignored;
    
    document.getElementById('tabAll').textContent = total;
    document.getElementById('tabPending').textContent = pending;
    document.getElementById('tabConfirmed').textContent = confirmed;
    document.getElementById('tabIgnored').textContent = ignored;
}

function searchAlerts() {
    const alertLevel = document.getElementById('alertLevel').value;
    const sourceIp = document.getElementById('sourceIp').value;
    const attackType = document.getElementById('attackType').value;
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    const params = {};
    if (alertLevel) params.alertLevel = alertLevel;
    if (sourceIp) params.sourceIp = sourceIp;
    if (attackType) params.attackType = attackType;
    if (startDate) params.startTime = startDate + ' 00:00:00';
    if (endDate) params.endTime = endDate + ' 23:59:59';
    
    const currentTab = document.querySelector('.tab-item.active');
    if (currentTab && currentTab.dataset.status !== undefined) {
        params.status = currentTab.dataset.status;
    }
    
    alertTable.search(params);
}

function resetSearch() {
    document.getElementById('alertLevel').value = '';
    document.getElementById('sourceIp').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('startDate').value = '';
    document.getElementById('endDate').value = '';
    alertTable.resetSearch();
}

function switchTab(element, status) {
    document.querySelectorAll('.tab-item').forEach(function(tab) {
        tab.classList.remove('active');
    });
    element.classList.add('active');
    
    const params = {};
    if (status !== '') {
        params.status = status;
    }
    alertTable.search(params);
}

function viewAlert(id) {
    currentAlertId = id;
    http.get('/alert/' + id)
        .then(function(alert) {
            showAlertDetail(alert);
        })
        .catch(function(error) {
            console.error('获取告警详情失败:', error);
            message.error('获取详情失败');
        });
}

function showAlertDetail(alert) {
    const content = document.getElementById('alertDetailContent');
    
    const levelClass = getLevelClass(alert.alertLevel);
    const statusClass = getStatusClass(alert.status);
    
    let html = '<div class="alert-detail-row"><div class="alert-detail-label">告警ID</div><div class="alert-detail-value">' + escapeHtml(alert.alertId) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">告警级别</div><div class="alert-detail-value"><span class="tag ' + levelClass + '">' + (alert.alertLevelChinese || alert.alertLevel) + '</span></div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">告警标题</div><div class="alert-detail-value">' + escapeHtml(alert.alertTitle) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">源IP</div><div class="alert-detail-value">' + escapeHtml(alert.sourceIp) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">攻击类型</div><div class="alert-detail-value"><span class="tag info">' + (alert.attackTypeChinese || alert.attackType || '-') + '</span></div></div>';
    if (alert.eventId) {
        html += '<div class="alert-detail-row"><div class="alert-detail-label">事件ID</div><div class="alert-detail-value">' + escapeHtml(alert.eventId) + '</div></div>';
    }
    if (alert.attackId) {
        html += '<div class="alert-detail-row"><div class="alert-detail-label">攻击记录</div><div class="alert-detail-value"><a href="/attack?attackId=' + alert.attackId + '" style="color: #4f46e5;">查看攻击记录</a></div></div>';
    }
    html += '<div class="alert-detail-row"><div class="alert-detail-label">创建时间</div><div class="alert-detail-value">' + formatDateTime(alert.createTime) + '</div></div>';
    html += '<div class="alert-detail-row"><div class="alert-detail-label">状态</div><div class="alert-detail-value"><span class="tag ' + statusClass + '">' + (alert.statusChinese || getStatusText(alert.status)) + '</span></div></div>';
    
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
        .then(function() {
            message.success('确认成功');
            closeAlertDetail();
            loadStats();
            alertTable.refresh();
        })
        .catch(function(error) {
            console.error('确认告警失败:', error);
            message.error('确认失败');
        });
}

function quickConfirm(id) {
    http.post('/alert/' + id + '/confirm')
        .then(function() {
            message.success('确认成功');
            loadStats();
            alertTable.refresh();
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
        .then(function() {
            message.success('已忽略');
            closeIgnoreModal();
            closeAlertDetail();
            loadStats();
            alertTable.refresh();
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
    if (allCheckboxes.length > 0) {
        selectAll.checked = selectedIds.length === allCheckboxes.length;
    }
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
        .then(function() {
            message.success('批量确认成功');
            loadStats();
            alertTable.refresh();
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
        .then(function() {
            message.success('批量删除成功');
            loadStats();
            alertTable.refresh();
        })
        .catch(function(error) {
            console.error('批量删除失败:', error);
            message.error('批量删除失败');
        });
}

function getLevelClass(level) {
    switch (level) {
        case 'CRITICAL': return 'danger';
        case 'HIGH': return 'warning';
        case 'MEDIUM': return 'info';
        case 'LOW': return 'success';
        default: return 'info';
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
        case 0: return 'warning';
        case 1: return 'success';
        case 2: return 'secondary';
        default: return 'info';
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
