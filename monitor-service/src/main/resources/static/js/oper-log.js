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
        actionWidth: '100px',
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
                    <td>${item.id}</td>
                    <td>${item.username || '-'}</td>
                    <td>${renderOperType(item.operType)}</td>
                    <td>${item.operModule || '-'}</td>
                    ${cell.renderCell(item.operContent, { maxLength: 30 })}
                    <td>${item.operIp || '-'}</td>
                    <td>${renderOperStatus(item.operStatus)}</td>
                    <td>${dateFormat.format(item.operTime)}</td>
                    ${cell.renderActionCell(`
                        ${cell.renderButton('详情', 'primary', `showDetail(${item.id})`)}
                    `, { width: '100px' })}
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
        'EXPORT': '<span class="tag">导出</span>'
    };
    return typeMap[type] || `<span class="tag">${type || '-'}</span>`;
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
