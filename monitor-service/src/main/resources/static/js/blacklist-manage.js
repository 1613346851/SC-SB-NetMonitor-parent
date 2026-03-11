let currentPage = 1;
const pageSize = 10;
let searchParams = {};
let defaultExpireSeconds = 86400;

document.addEventListener('DOMContentLoaded', function() {
    loadDefaultExpireTime();
    loadBlacklistData();
});

async function loadDefaultExpireTime() {
    try {
        const result = await http.get('/config/value/blacklist.default.expire.seconds');
        if (result && result.data) {
            defaultExpireSeconds = parseInt(result.data);
            updateExpireTimeHint();
        }
    } catch (error) {
        console.error('加载默认过期时间失败:', error);
    }
}

function updateExpireTimeHint() {
    const hintElement = document.querySelector('#blacklistForm small');
    if (hintElement) {
        const hours = Math.floor(defaultExpireSeconds / 3600);
        const minutes = Math.floor((defaultExpireSeconds % 3600) / 60);
        const seconds = defaultExpireSeconds % 60;
        
        let hintText = '留空或全部为0表示使用默认时长（';
        if (hours > 0) {
            hintText += hours + '小时';
        }
        if (minutes > 0) {
            hintText += minutes + '分';
        }
        if (seconds > 0) {
            hintText += seconds + '秒';
        }
        if (hours === 0 && minutes === 0 && seconds === 0) {
            hintText += '0秒';
        }
        hintText += '）';
        
        hintElement.textContent = hintText;
    }
}

async function loadBlacklistData() {
    try {
        const params = {
            page: currentPage,
            size: pageSize,
            ...searchParams
        };

        const result = await http.get('/blacklist/list', params);
        
        renderBlacklistTable(result.list || result || []);
        renderPagination(result.total || 0);
    } catch (error) {
        console.error('加载黑名单数据失败:', error);
    }
}

function renderBlacklistTable(data) {
    const tbody = document.getElementById('blacklistTableBody');
    
    if (!data || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = data.map((item, index) => `
        <tr>
            <td>${index + 1}</td>
            <td>${item.ip || '-'}</td>
            <td>${item.reason || '-'}</td>
            <td>${item.expireTime ? dateFormat.format(item.expireTime) : '永久'}</td>
            <td>${renderStatus(item.status)}</td>
            <td>${item.createTime ? dateFormat.format(item.createTime) : '-'}</td>
            <td>
                <button class="btn btn-danger btn-sm" onclick="removeFromBlacklist('${item.ip}')">移除</button>
            </td>
        </tr>
    `).join('');
}

function renderStatus(status) {
    if (status === 1) {
        return '<span class="tag danger">生效中</span>';
    } else if (status === 2) {
        return '<span class="tag info">已过期</span>';
    } else {
        return '<span class="tag info">未知</span>';
    }
}

function renderPagination(total) {
    const totalPages = Math.ceil(total / pageSize);
    const paginationEl = document.getElementById('pagination');
    
    let html = `
        <span class="pagination-item ${currentPage === 1 ? 'disabled' : ''}" 
              onclick="goPage(${currentPage - 1})">上一页</span>
    `;
    
    for (let i = 1; i <= totalPages; i++) {
        if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
            html += `
                <span class="pagination-item ${i === currentPage ? 'active' : ''}" 
                      onclick="goPage(${i})">${i}</span>
            `;
        } else if (i === currentPage - 2 || i === currentPage + 2) {
            html += `<span class="pagination-item">...</span>`;
        }
    }
    
    html += `
        <span class="pagination-item ${currentPage === totalPages ? 'disabled' : ''}" 
              onclick="goPage(${currentPage + 1})">下一页</span>
        <span class="pagination-item">共 ${total} 条</span>
    `;
    
    paginationEl.innerHTML = html;
}

function goPage(page) {
    const totalPages = Math.ceil((searchParams.total || 10) / pageSize);
    
    if (page < 1 || page > totalPages || page === currentPage) {
        return;
    }
    
    currentPage = page;
    loadBlacklistData();
}

function searchBlacklist() {
    const ipAddress = document.getElementById('ipAddress').value.trim();
    
    searchParams = {};
    
    if (ipAddress) searchParams.ipAddress = ipAddress;
    
    currentPage = 1;
    loadBlacklistData();
}

function resetSearch() {
    document.getElementById('ipAddress').value = '';
    
    searchParams = {};
    currentPage = 1;
    loadBlacklistData();
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

    const totalSeconds = 
        years * 365 * 24 * 60 * 60 +
        months * 30 * 24 * 60 * 60 +
        days * 24 * 60 * 60 +
        hours * 60 * 60 +
        minutes * 60 +
        seconds;

    return totalSeconds;
}

function validateExpireInputs() {
    const years = parseInt(document.getElementById('expireYears').value);
    const months = parseInt(document.getElementById('expireMonths').value);
    const days = parseInt(document.getElementById('expireDays').value);
    const hours = parseInt(document.getElementById('expireHours').value);
    const minutes = parseInt(document.getElementById('expireMinutes').value);
    const seconds = parseInt(document.getElementById('expireSeconds').value);

    const errors = [];

    if (years !== undefined && (years < 0 || years > 99)) {
        errors.push('年必须在0-99之间');
    }

    if (months !== undefined && (months < 0 || months > 11)) {
        errors.push('月必须在0-11之间');
    }

    if (days !== undefined && (days < 0 || days > 30)) {
        errors.push('日必须在0-30之间');
    }

    if (hours !== undefined && (hours < 0 || hours > 23)) {
        errors.push('时必须在0-23之间');
    }

    if (minutes !== undefined && (minutes < 0 || minutes > 59)) {
        errors.push('分必须在0-59之间');
    }

    if (seconds !== undefined && (seconds < 0 || seconds > 59)) {
        errors.push('秒必须在0-59之间');
    }

    return errors;
}

async function saveBlacklist() {
    const ipAddress = document.getElementById('ipInput').value.trim();
    const reason = document.getElementById('reasonInput').value.trim();
    const isPermanent = document.getElementById('permanentBlacklist').checked;

    if (!ipAddress) {
        message.error('请输入 IP 地址');
        return;
    }

    const ipRegex = /^(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])$/;
    if (!ipRegex.test(ipAddress)) {
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

    try {
        const data = {
            ipAddress: ipAddress,
            reason: reason || '手动添加'
        };

        if (isPermanent) {
            data.expireSeconds = null;
        } else {
            const totalSeconds = convertToTotalSeconds();
            if (totalSeconds > 0) {
                data.expireSeconds = totalSeconds;
            } else {
                data.expireSeconds = null;
            }
        }

        await http.post('/blacklist', data);
        
        message.success('添加成功');
        closeBlacklistModal();
        loadBlacklistData();
    } catch (error) {
        console.error('添加黑名单失败:', error);
        message.error(error.message || '添加失败');
    }
}

async function removeFromBlacklist(ip) {
    if (!confirm('确定要从黑名单中移除该 IP 吗？')) {
        return;
    }

    try {
        await http.delete(`/blacklist/${ip}`);
        message.success('移除成功');
        loadBlacklistData();
    } catch (error) {
        console.error('移除黑名单失败:', error);
        message.error(error.message || '移除失败');
    }
}

async function cleanExpiredBlacklist() {
    if (!confirm('确定要清理所有过期的黑名单吗？')) {
        return;
    }

    try {
        const result = await http.post('/blacklist/clean-expired');
        message.success(`清理完成，共清理 ${result.cleanedCount} 条记录`);
        loadBlacklistData();
    } catch (error) {
        console.error('清理过期黑名单失败:', error);
        message.error(error.message || '清理失败');
    }
}
