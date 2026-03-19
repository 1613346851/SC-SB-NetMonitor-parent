/**
 * 操作日志页面 JavaScript
 */

document.addEventListener('DOMContentLoaded', function() {
    const today = new Date().toISOString().split('T')[0];
    const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    
    document.getElementById('searchEndDate').value = today;
    document.getElementById('searchStartDate').value = weekAgo;
    
    loadLogs();
});

async function loadLogs() {
    try {
        const username = document.getElementById('searchUsername').value;
        const operType = document.getElementById('searchOperType').value;
        const operStatus = document.getElementById('searchOperStatus').value;
        const startTime = document.getElementById('searchStartDate').value;
        const endTime = document.getElementById('searchEndDate').value;
        
        const params = {};
        if (username) params.username = username;
        if (operType) params.operType = operType;
        if (operStatus !== '') params.operStatus = operStatus;
        if (startTime) params.startTime = startTime + ' 00:00:00';
        if (endTime) params.endTime = endTime + ' 23:59:59';
        
        const logs = await http.get('/system/log/list', params);
        renderLogTable(logs);
    } catch (error) {
        console.error('加载日志列表失败:', error);
        document.getElementById('logTableBody').innerHTML = 
            '<tr><td colspan="9" class="text-center text-danger">加载失败</td></tr>';
    }
}

function renderLogTable(logs) {
    const tbody = document.getElementById('logTableBody');
    
    if (!logs || logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = logs.map(log => `
        <tr>
            <td>${log.id}</td>
            <td>${log.username || '-'}</td>
            <td>${renderOperType(log.operType)}</td>
            <td>${log.operModule || '-'}</td>
            <td title="${log.operContent || ''}">${truncateText(log.operContent || '-', 30)}</td>
            <td>${log.operIp || '-'}</td>
            <td>${renderOperStatus(log.operStatus)}</td>
            <td>${dateFormat.format(log.operTime)}</td>
            <td>
                <button class="btn btn-primary btn-sm" onclick="showDetail(${log.id})">详情</button>
            </td>
        </tr>
    `).join('');
}

function renderOperType(type) {
    const typeMap = {
        'LOGIN': '<span class="tag primary">登录</span>',
        'LOGOUT': '<span class="tag info">登出</span>',
        'INSERT': '<span class="tag success">新增</span>',
        'UPDATE': '<span class="tag warning">修改</span>',
        'DELETE': '<span class="tag danger">删除</span>',
        'EXPORT': '<span class="tag">导出</span>'
    };
    return typeMap[type] || `<span class="tag">${type || '-'}</span>`;
}

function renderOperStatus(status) {
    return status === 0 ? 
        '<span class="tag success">成功</span>' : 
        '<span class="tag danger">失败</span>';
}

function truncateText(text, maxLength) {
    if (!text) return '-';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
}

function searchLogs() {
    loadLogs();
}

function resetSearch() {
    document.getElementById('searchUsername').value = '';
    document.getElementById('searchOperType').value = '';
    document.getElementById('searchOperStatus').value = '';
    
    const today = new Date().toISOString().split('T')[0];
    const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    document.getElementById('searchEndDate').value = today;
    document.getElementById('searchStartDate').value = weekAgo;
    
    loadLogs();
}

async function showDetail(id) {
    try {
        const log = await http.get(`/system/log/${id}`);
        
        const content = document.getElementById('logDetailContent');
        content.innerHTML = `
            <div class="detail-grid">
                <div class="detail-item">
                    <label>日志ID：</label>
                    <span>${log.id}</span>
                </div>
                <div class="detail-item">
                    <label>操作账号：</label>
                    <span>${log.username || '-'}</span>
                </div>
                <div class="detail-item">
                    <label>操作类型：</label>
                    <span>${renderOperType(log.operType)}</span>
                </div>
                <div class="detail-item">
                    <label>操作模块：</label>
                    <span>${log.operModule || '-'}</span>
                </div>
                <div class="detail-item full-width">
                    <label>操作内容：</label>
                    <span>${log.operContent || '-'}</span>
                </div>
                <div class="detail-item">
                    <label>请求方法：</label>
                    <span>${log.operMethod || '-'}</span>
                </div>
                <div class="detail-item">
                    <label>请求URL：</label>
                    <span>${log.operUrl || '-'}</span>
                </div>
                <div class="detail-item">
                    <label>操作IP：</label>
                    <span>${log.operIp || '-'}</span>
                </div>
                <div class="detail-item">
                    <label>操作地点：</label>
                    <span>${log.operLocation || '-'}</span>
                </div>
                <div class="detail-item">
                    <label>操作状态：</label>
                    <span>${renderOperStatus(log.operStatus)}</span>
                </div>
                <div class="detail-item">
                    <label>操作时间：</label>
                    <span>${dateFormat.format(log.operTime)}</span>
                </div>
                <div class="detail-item">
                    <label>耗时：</label>
                    <span>${log.costTime ? log.costTime + 'ms' : '-'}</span>
                </div>
                ${log.errorMsg ? `
                <div class="detail-item full-width">
                    <label>错误信息：</label>
                    <span class="text-danger">${log.errorMsg}</span>
                </div>
                ` : ''}
            </div>
        `;
        
        document.getElementById('detailModal').style.display = 'flex';
    } catch (error) {
        console.error('获取日志详情失败:', error);
        message.error('获取日志详情失败');
    }
}

function closeDetailModal() {
    document.getElementById('detailModal').style.display = 'none';
}

document.getElementById('detailModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeDetailModal();
    }
});
