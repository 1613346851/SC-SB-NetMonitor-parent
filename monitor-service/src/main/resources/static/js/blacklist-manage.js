let blacklistTable;
let defaultExpireSeconds = 86400;

document.addEventListener('DOMContentLoaded', function() {
    loadDefaultExpireTime();
    initBlacklistTable();
});

async function loadDefaultExpireTime() {
    try {
        const result = await http.get('/config/value/blacklist.default.expire.seconds');
        if (result) {
            defaultExpireSeconds = parseInt(result);
            updateExpireTimeHint();
        }
    } catch (error) {
        console.error('加载默认过期时间失败:', error);
    }
}

function updateExpireTimeHint() {
    const hintElement = document.getElementById('expireTimeHint');
    if (hintElement) {
        const hours = Math.floor(defaultExpireSeconds / 3600);
        const minutes = Math.floor((defaultExpireSeconds % 3600) / 60);
        const seconds = defaultExpireSeconds % 60;
        
        let hintText = '留空或全部为0表示使用默认时长（';
        if (hours > 0) hintText += hours + '小时';
        if (minutes > 0) hintText += minutes + '分';
        if (seconds > 0) hintText += seconds + '秒';
        if (hours === 0 && minutes === 0 && seconds === 0) hintText += '0秒';
        hintText += '）';
        
        hintElement.textContent = hintText;
    }
}

function initBlacklistTable() {
    blacklistTable = TableUtils.createInstance({
        instanceName: 'blacklistTable',
        apiUrl: '/blacklist/list',
        pageSize: 10,
        defaultSortField: 'createTime',
        defaultSortOrder: 'desc',
        tableBodyEl: 'blacklistTableBody',
        paginationEl: 'pagination',
        colspan: 9,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item, index) {
            const cell = TableUtils.cell;
            const ipId = item.ip.replace(/\./g, '-');
            
            return `
                <tr class="main-row" data-ip="${item.ip}">
                    <td>${item.id || index + 1}</td>
                    <td>
                        <span class="ip-link" onclick="toggleHistory('${item.ip}')" style="cursor: pointer; color: #4f46e5; text-decoration: underline;">
                            ${cell.renderText(item.ip)}
                        </span>
                    </td>
                    ${cell.renderCell(item.reason, { maxLength: 20 })}
                    <td>${item.expireTime ? DateUtil.format(item.expireTime) : '<span class="tag danger">永久</span>'}</td>
                    <td>${cell.renderText(item.remainingTime)}</td>
                    <td>${item.totalBanCount || 0} 次</td>
                    <td>${renderBlacklistStatus(item.status)}</td>
                    <td>${item.createTime ? DateUtil.format(item.createTime) : '-'}</td>
                    ${cell.renderActionCell([
                        { text: '解禁', type: 'warning', onClick: `showUnblockModal('${item.ip}')` },
                        { text: '延长', type: 'primary', onClick: `showExtendModal('${item.ip}')` },
                        { text: '删除', type: 'danger', onClick: `deleteAllBlacklists('${item.ip}')` }
                    ])}
                </tr>
                <tr class="history-row" id="history-${ipId}" style="display: none;">
                    <td colspan="9">
                        <div class="history-container" style="padding: 16px; background: #fafafa; border-radius: 8px; margin-top: 8px;">
                            <h4 style="margin: 0 0 12px 0; font-size: 14px; color: #333; font-weight: 600;">封禁历史记录</h4>
                            <table class="history-table" style="width: 100%; border-collapse: collapse; background: #fff; border-radius: 4px; overflow: hidden;">
                                <thead>
                                    <tr style="background: #f5f5f5;">
                                        <th style="padding: 10px 12px; text-align: left; border-bottom: 1px solid #e8e8e8; font-weight: 500; color: #666;">ID</th>
                                        <th style="padding: 10px 12px; text-align: left; border-bottom: 1px solid #e8e8e8; font-weight: 500; color: #666;">拉黑原因</th>
                                        <th style="padding: 10px 12px; text-align: left; border-bottom: 1px solid #e8e8e8; font-weight: 500; color: #666;">封禁时长</th>
                                        <th style="padding: 10px 12px; text-align: left; border-bottom: 1px solid #e8e8e8; font-weight: 500; color: #666;">过期时间</th>
                                        <th style="padding: 10px 12px; text-align: left; border-bottom: 1px solid #e8e8e8; font-weight: 500; color: #666;">状态</th>
                                        <th style="padding: 10px 12px; text-align: left; border-bottom: 1px solid #e8e8e8; font-weight: 500; color: #666;">创建时间</th>
                                        <th style="padding: 10px 12px; text-align: left; border-bottom: 1px solid #e8e8e8; font-weight: 500; color: #666;">操作人</th>
                                    </tr>
                                </thead>
                                <tbody id="history-body-${ipId}">
                                    <tr><td colspan="7" class="text-center" style="padding: 20px; color: #999;">加载中...</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </td>
                </tr>
            `;
        }
    });
    
    window.blacklistTable = blacklistTable;
    blacklistTable.loadData();
}

function renderBlacklistStatus(status) {
    if (status === 1) {
        return '<span class="tag danger">生效中</span>';
    } else if (status === 2) {
        return '<span class="tag info">已过期</span>';
    }
    return '<span class="tag info">未知</span>';
}

function searchBlacklist() {
    const ipAddress = blacklistTable.getSearchValue('ipAddress');
    const params = {};
    if (ipAddress) params.ipAddress = ipAddress;
    blacklistTable.search(params);
}

function resetSearch() {
    document.getElementById('ipAddress').value = '';
    blacklistTable.resetSearch();
}

function showAddBlacklistModal() {
    document.getElementById('blacklistForm').reset();
    document.getElementById('blacklistModal').style.display = 'flex';
}

function closeBlacklistModal() {
    document.getElementById('blacklistModal').style.display = 'none';
}

function convertToTotalSeconds() {
    const years = parseInt(document.getElementById('expireYears').value) || 0;
    const months = parseInt(document.getElementById('expireMonths').value) || 0;
    const days = parseInt(document.getElementById('expireDays').value) || 0;
    const hours = parseInt(document.getElementById('expireHours').value) || 0;
    const minutes = parseInt(document.getElementById('expireMinutes').value) || 0;
    const seconds = parseInt(document.getElementById('expireSeconds').value) || 0;

    return years * 365 * 24 * 60 * 60 +
           months * 30 * 24 * 60 * 60 +
           days * 24 * 60 * 60 +
           hours * 60 * 60 +
           minutes * 60 +
           seconds;
}

function validateExpireInputs() {
    const years = parseInt(document.getElementById('expireYears').value);
    const months = parseInt(document.getElementById('expireMonths').value);
    const days = parseInt(document.getElementById('expireDays').value);
    const hours = parseInt(document.getElementById('expireHours').value);
    const minutes = parseInt(document.getElementById('expireMinutes').value);
    const seconds = parseInt(document.getElementById('expireSeconds').value);

    const errors = [];
    if (!isNaN(years) && (years < 0 || years > 99)) errors.push('年必须在0-99之间');
    if (!isNaN(months) && (months < 0 || months > 11)) errors.push('月必须在0-11之间');
    if (!isNaN(days) && (days < 0 || days > 30)) errors.push('日必须在0-30之间');
    if (!isNaN(hours) && (hours < 0 || hours > 23)) errors.push('时必须在0-23之间');
    if (!isNaN(minutes) && (minutes < 0 || minutes > 59)) errors.push('分必须在0-59之间');
    if (!isNaN(seconds) && (seconds < 0 || seconds > 59)) errors.push('秒必须在0-59之间');
    return errors;
}

let isSavingBlacklist = false;

async function saveBlacklist() {
    if (isSavingBlacklist) return;
    
    const ipAddress = document.getElementById('ipInput').value.trim();
    const reason = document.getElementById('reasonInput').value.trim();
    const isPermanent = document.getElementById('permanentBlacklist').checked;

    if (!ipAddress) {
        message.error('请输入 IP 地址');
        return;
    }

    const ipv4Regex = /^(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])$/;
    const ipv6Regex = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{1,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$/;

    if (!ipv4Regex.test(ipAddress) && !ipv6Regex.test(ipAddress)) {
        message.error('IP 地址格式不正确');
        return;
    }

    if (!isPermanent) {
        const validationErrors = validateExpireInputs();
        if (validationErrors.length > 0) {
            message.error(validationErrors.join('；'));
            return;
        }
    }

    isSavingBlacklist = true;
    const saveBtn = document.querySelector('#blacklistModal .modal-footer .btn-primary');
    const originalBtnText = saveBtn.textContent;
    saveBtn.textContent = '保存中...';
    saveBtn.disabled = true;

    try {
        const data = {
            ipAddress: ipAddress,
            reason: reason || '手动添加',
            isPermanent: isPermanent
        };

        if (!isPermanent) {
            const totalSeconds = convertToTotalSeconds();
            if (totalSeconds > 0) data.expireSeconds = totalSeconds;
        }

        await http.post('/blacklist', data);
        message.success('添加成功');
        closeBlacklistModal();
        blacklistTable.refresh();
    } catch (error) {
        console.error('添加黑名单失败:', error);
        message.error(error.message || '添加失败');
    } finally {
        isSavingBlacklist = false;
        saveBtn.textContent = originalBtnText;
        saveBtn.disabled = false;
    }
}

async function removeFromBlacklist(ip) {
    if (!confirm('确定要从黑名单中移除该 IP 吗？')) return;

    try {
        await http.delete(`/blacklist/${ip}`);
        message.success('移除成功');
        blacklistTable.refresh();
    } catch (error) {
        console.error('移除黑名单失败:', error);
        message.error(error.message || '移除失败');
    }
}

async function cleanExpiredBlacklist() {
    if (!confirm('确定要清理所有过期的黑名单吗？')) return;

    try {
        const result = await http.post('/blacklist/clean-expired');
        message.success(`清理完成，共清理 ${result.cleanedCount} 条记录`);
        blacklistTable.refresh();
    } catch (error) {
        console.error('清理过期黑名单失败:', error);
        message.error(error.message || '清理失败');
    }
}

async function toggleHistory(ip) {
    const ipId = ip.replace(/\./g, '-');
    const historyRow = document.getElementById(`history-${ipId}`);
    if (historyRow) {
        const isVisible = historyRow.style.display !== 'none';
        historyRow.style.display = isVisible ? 'none' : 'table-row';
        if (!isVisible) await loadHistory(ip);
    }
}

async function loadHistory(ip) {
    try {
        const result = await http.get(`/blacklist/${ip}/history`);
        const ipId = ip.replace(/\./g, '-');
        const historyBody = document.getElementById(`history-body-${ipId}`);
        
        if (historyBody && result.history) {
            historyBody.innerHTML = result.history.map(item => `
                <tr>
                    <td style="padding: 10px 12px; border-bottom: 1px solid #e8e8e8;">${item.id || '-'}</td>
                    <td style="padding: 10px 12px; border-bottom: 1px solid #e8e8e8;">${item.reason || '-'}</td>
                    <td style="padding: 10px 12px; border-bottom: 1px solid #e8e8e8;">${item.banDurationText || '永久'}</td>
                    <td style="padding: 10px 12px; border-bottom: 1px solid #e8e8e8;">${item.expireTime ? DateUtil.format(item.expireTime) : '永久'}</td>
                    <td style="padding: 10px 12px; border-bottom: 1px solid #e8e8e8;">${renderBlacklistStatus(item.status)}</td>
                    <td style="padding: 10px 12px; border-bottom: 1px solid #e8e8e8;">${item.createTime ? DateUtil.format(item.createTime) : '-'}</td>
                    <td style="padding: 10px 12px; border-bottom: 1px solid #e8e8e8;">${item.operator || '-'}</td>
                </tr>
            `).join('');
        }
    } catch (error) {
        console.error('加载历史记录失败:', error);
        message.error(error.message || '加载历史记录失败');
    }
}

function showExtendModal(ip) {
    document.getElementById('extendIpInput').value = ip;
    document.getElementById('extendIpDisplay').value = ip;
    document.getElementById('extendExpireYears').value = '';
    document.getElementById('extendExpireMonths').value = '';
    document.getElementById('extendExpireDays').value = '';
    document.getElementById('extendExpireHours').value = '';
    document.getElementById('extendExpireMinutes').value = '';
    document.getElementById('extendExpireSeconds').value = '';
    document.getElementById('extendModal').style.display = 'flex';
}

function closeExtendModal() {
    document.getElementById('extendModal').style.display = 'none';
}

let isExtendingBlacklist = false;

async function extendBlacklist() {
    if (isExtendingBlacklist) return;
    
    const ip = document.getElementById('extendIpInput').value;
    const totalSeconds = convertExtendToTotalSeconds();

    if (totalSeconds <= 0) {
        message.error('请输入有效的延长时间');
        return;
    }

    isExtendingBlacklist = true;
    const extendBtn = document.querySelector('#extendModal .modal-footer .btn-primary');
    const originalBtnText = extendBtn.textContent;
    extendBtn.textContent = '处理中...';
    extendBtn.disabled = true;

    try {
        await http.put(`/blacklist/${ip}/extend`, { expireSeconds: totalSeconds });
        message.success('延长封禁时间成功');
        closeExtendModal();
        blacklistTable.refresh();
    } catch (error) {
        console.error('延长封禁时间失败:', error);
        message.error(error.message || '延长失败');
    } finally {
        isExtendingBlacklist = false;
        extendBtn.textContent = originalBtnText;
        extendBtn.disabled = false;
    }
}

function convertExtendToTotalSeconds() {
    const years = parseInt(document.getElementById('extendExpireYears').value) || 0;
    const months = parseInt(document.getElementById('extendExpireMonths').value) || 0;
    const days = parseInt(document.getElementById('extendExpireDays').value) || 0;
    const hours = parseInt(document.getElementById('extendExpireHours').value) || 0;
    const minutes = parseInt(document.getElementById('extendExpireMinutes').value) || 0;
    const seconds = parseInt(document.getElementById('extendExpireSeconds').value) || 0;

    return years * 365 * 24 * 60 * 60 +
           months * 30 * 24 * 60 * 60 +
           days * 24 * 60 * 60 +
           hours * 60 * 60 +
           minutes * 60 +
           seconds;
}

async function deleteAllBlacklists(ip) {
    if (!confirm(`确定要删除 IP ${ip} 的所有黑名单记录吗？此操作不可恢复！`)) return;

    try {
        const result = await http.delete(`/blacklist/${ip}/all`);
        message.success(`删除成功，共删除 ${result.deletedCount} 条记录`);
        blacklistTable.refresh();
    } catch (error) {
        console.error('删除黑名单记录失败:', error);
        message.error(error.message || '删除失败');
    }
}

async function deleteSingleHistory(ip, id) {
    if (!confirm('确定要删除这条封禁记录吗？')) return;

    try {
        await http.delete(`/blacklist/${ip}`);
        message.success('删除成功');
        await loadHistory(ip);
        blacklistTable.refresh();
    } catch (error) {
        console.error('删除封禁记录失败:', error);
        message.error(error.message || '删除失败');
    }
}

function showUnblockModal(ip) {
    document.getElementById('unblockIpInput').value = ip;
    document.getElementById('unblockIpDisplay').value = ip;
    document.getElementById('unblockModal').style.display = 'flex';
}

function closeUnblockModal() {
    document.getElementById('unblockModal').style.display = 'none';
}

let isUnblocking = false;

async function unblockIp() {
    if (isUnblocking) return;
    
    const ip = document.getElementById('unblockIpInput').value;

    isUnblocking = true;
    const unblockBtn = document.querySelector('#unblockModal .modal-footer .btn-warning');
    const originalBtnText = unblockBtn.textContent;
    unblockBtn.textContent = '处理中...';
    unblockBtn.disabled = true;

    try {
        await http.post(`/blacklist/${ip}/unblock`);
        message.success('解禁成功');
        closeUnblockModal();
        blacklistTable.refresh();
    } catch (error) {
        console.error('解禁失败:', error);
        message.error(error.message || '解禁失败');
    } finally {
        isUnblocking = false;
        unblockBtn.textContent = originalBtnText;
        unblockBtn.disabled = false;
    }
}
