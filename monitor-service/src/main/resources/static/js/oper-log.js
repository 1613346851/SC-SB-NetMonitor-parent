/**
 * 操作日志页面 JavaScript
 */

let operLogTable;

document.addEventListener('DOMContentLoaded', function() {
    const today = new Date().toISOString().split('T')[0];
    const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    
    document.getElementById('searchEndDate').value = today;
    document.getElementById('searchStartDate').value = weekAgo;
    
    initOperLogTable();
});

function initOperLogTable() {
    operLogTable = TableUtils.createInstance({
        instanceName: 'operLogTable',
        apiUrl: '/system/log/list',
        pageSize: 10,
        defaultSortField: 'operTime',
        defaultSortOrder: 'desc',
        tableBodyEl: 'logTableBody',
        paginationEl: 'pagination',
        colspan: 9,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
                    <td>${item.id || '-'}</td>
                    ${cell.renderCell(item.username, { maxLength: 20 })}
                    <td>${renderOperType(item.operType)}</td>
                    ${cell.renderCell(item.operModule, { maxLength: 20 })}
                    ${cell.renderCell(item.operContent, { maxLength: 30 })}
                    ${cell.renderCell(item.operIp, { maxLength: 20 })}
                    <td>${renderOperStatus(item.operStatus)}</td>
                    <td>${dateFormat.format(item.operTime)}</td>
                    ${cell.renderActionCell([
                        { text: '详情', type: 'primary', onClick: `showDetail(${item.id})` }
                    ])}
                </tr>
            `;
        }
    });
    
    window.operLogTable = operLogTable;
    operLogTable.loadData();
}

function renderOperType(type) {
    const typeMap = {
        'LOGIN': '<span class="tag primary">登录</span>',
        'LOGOUT': '<span class="tag info">登出</span>',
        'INSERT': '<span class="tag success">新增</span>',
        'UPDATE': '<span class="tag warning">修改</span>',
        'DELETE': '<span class="tag danger">删除</span>',
        'EXPORT': '<span class="tag secondary">导出</span>'
    };
    return typeMap[type] || `<span class="tag secondary">${type || '-'}</span>`;
}

function renderOperStatus(status) {
    return status === 0 ? 
        '<span class="tag success">成功</span>' : 
        '<span class="tag danger">失败</span>';
}

function searchLogs() {
    const username = operLogTable.getSearchValue('searchUsername');
    const operType = operLogTable.getSearchSelectValue('searchOperType');
    const operStatus = operLogTable.getSearchSelectValue('searchOperStatus');
    const startTime = operLogTable.getSearchValue('searchStartDate');
    const endTime = operLogTable.getSearchValue('searchEndDate');
    
    const params = {};
    if (username) params.username = username;
    if (operType) params.operType = operType;
    if (operStatus !== '') params.operStatus = operStatus;
    if (startTime) params.startTime = startTime + ' 00:00:00';
    if (endTime) params.endTime = endTime + ' 23:59:59';
    
    operLogTable.search(params);
}

function resetSearch() {
    document.getElementById('searchUsername').value = '';
    document.getElementById('searchOperType').value = '';
    document.getElementById('searchOperStatus').value = '';
    
    const today = new Date().toISOString().split('T')[0];
    const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    document.getElementById('searchEndDate').value = today;
    document.getElementById('searchStartDate').value = weekAgo;
    
    operLogTable.resetSearch();
}

function getExportParams() {
    const params = new URLSearchParams();
    const searchParams = operLogTable.searchParams || {};
    
    if (searchParams.username) params.append('username', searchParams.username);
    if (searchParams.operType) params.append('operType', searchParams.operType);
    if (searchParams.operStatus !== undefined && searchParams.operStatus !== '') params.append('operStatus', searchParams.operStatus);
    if (searchParams.startTime) params.append('startTime', searchParams.startTime);
    if (searchParams.endTime) params.append('endTime', searchParams.endTime);
    
    return params.toString();
}

async function exportLogs() {
    const exportBtn = document.getElementById('exportBtn');
    const originalText = exportBtn.innerHTML;
    
    try {
        exportBtn.disabled = true;
        exportBtn.innerHTML = '<span>⏳</span> 导出中...';
        
        const params = getExportParams();
        const token = AuthService.getToken();
        const url = `${AppConfig.API_BASE_URL}/system/log/export${params ? '?' + params : ''}`;
        
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Authorization': `${AppConfig.AUTH.TOKEN_PREFIX}${token}`
            }
        });
        
        if (!response.ok) {
            throw new Error('导出失败');
        }
        
        const blob = await response.blob();
        
        const contentDisposition = response.headers.get('Content-Disposition');
        let fileName = 'oper_log.csv';
        if (contentDisposition) {
            const match = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
            if (match && match[1]) {
                fileName = decodeURIComponent(match[1].replace(/['"]/g, ''));
            }
        }
        
        const link = document.createElement('a');
        link.href = window.URL.createObjectURL(blob);
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(link.href);
        
        message.success('导出成功');
    } catch (error) {
        console.error('导出操作日志失败:', error);
        message.error('导出失败，请稍后重试');
    } finally {
        exportBtn.disabled = false;
        exportBtn.innerHTML = originalText;
    }
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
